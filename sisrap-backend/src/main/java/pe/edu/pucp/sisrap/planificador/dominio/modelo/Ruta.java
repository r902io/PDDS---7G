package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/** Ruta de una unidad de transporte. */
public class Ruta {

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private Almacen almacenRetorno;
    private final Nodo posicionInicio;
    private final boolean pasarPorAlmacenOrigen;
    private final LocalDateTime inicio;
    private final ReglasPlanificacion reglas;

    private final List<Pedido> secuenciaPedidos = new ArrayList<>();

    private double distanciaTotalKm;
    private double costoTotal;
    private double tiempoTotalHoras;

    private Set<String> nodosBloqueados = Set.of();
    private List<Almacen> almacenesCandidatos = List.of();
    private boolean intransitable;
    private boolean fueraDeTurno;
    private boolean cargaYaRecogida;
    private Nodo nodoAnterior;
    private LocalDateTime disponibleHasta;
    private double tiempoCargaHoras;
    public void setTiempoCargaHoras(double horas) {
        if (!Double.isFinite(horas) || horas < 0) throw new IllegalArgumentException("Tiempo de carga inválido");
        tiempoCargaHoras=horas; evaluacionCache=null;
    }
    private Evaluacion evaluacionCache;
    private List<Pedido> pedidosCache = List.of();
    public void setDisponibleHasta(LocalDateTime hasta) {
        if (!java.util.Objects.equals(disponibleHasta, hasta)) evaluacionCache = null;
        disponibleHasta = hasta;
    }


    /**
     * Constructor histórico: conserva exactamente el comportamiento usado por
     * la experimentación. La ruta se considera iniciada en el almacén origen.
     */
    public Ruta(
            Vehiculo vehiculo,
            Almacen origen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {
        this(
                vehiculo,
                origen,
                origen.getUbicacion(),
                false,
                inicio,
                reglas);
    }

    /**
     * Constructor operativo. Cuando {@code pasarPorAlmacenOrigen=true}, el
     * vehículo parte desde su posición real y primero viaja al almacén elegido
     * por el planificador para recoger la carga.
     */
    public Ruta(
            Vehiculo vehiculo,
            Almacen origen,
            Nodo posicionInicio,
            boolean pasarPorAlmacenOrigen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {

        if (vehiculo == null
                || origen == null
                || posicionInicio == null
                || inicio == null
                || reglas == null) {
            throw new IllegalArgumentException("Ruta incompleta");
        }

        this.vehiculo = vehiculo;
        this.almacenOrigen = origen;
        this.almacenRetorno = origen;
        this.posicionInicio = posicionInicio;
        this.pasarPorAlmacenOrigen = pasarPorAlmacenOrigen;
        this.inicio = inicio;
        this.reglas = reglas;
    }

    public void setNodosBloqueados(Set<String> nodos) {
        Set<String> nuevos = nodos == null ? Set.of() : Set.copyOf(nodos);
        if (!nuevos.equals(this.nodosBloqueados)) evaluacionCache = null;
        this.nodosBloqueados = nuevos;
    }

    public Set<String> getNodosBloqueados() { return nodosBloqueados; }
    public boolean isIntransitable() { return intransitable; }
    public boolean isFueraDeTurno() { return fueraDeTurno; }

    public Ruta copiar() {
        Ruta copia = new Ruta(
                vehiculo,
                almacenOrigen,
                posicionInicio,
                pasarPorAlmacenOrigen,
                inicio,
                reglas);
        copia.disponibleHasta = disponibleHasta;
        copia.tiempoCargaHoras = tiempoCargaHoras;
        copia.cargaYaRecogida = cargaYaRecogida;
        copia.nodoAnterior = nodoAnterior;
        copia.nodosBloqueados = nodosBloqueados;
        copia.calendarioBloqueos = calendarioBloqueos;
        copia.almacenesCandidatos = almacenesCandidatos;
        copia.almacenRetorno = almacenRetorno;
        copia.secuenciaPedidos.addAll(secuenciaPedidos);
        copia.recalcular();
        return copia;
    }

    public int cargaTotal() {
        return secuenciaPedidos.stream()
                .mapToInt(Pedido::getCantidadQq)
                .sum();
    }

    private List<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo> calendarioBloqueos = List.of();
    public void setCalendarioBloqueos(List<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo> calendario) {
        var nuevos = calendario == null
                ? List.<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo>of() : List.copyOf(calendario);
        if (!nuevos.equals(calendarioBloqueos)) evaluacionCache = null;
        calendarioBloqueos = nuevos;
    }

    private record Evaluacion(List<LocalDateTime> arribos, List<LocalDateTime> entregas,
                               LocalDateTime fin, double distancia, boolean inaccesible) { }

    private Evaluacion evaluar() {
        if (evaluacionCache != null && pedidosCache.equals(secuenciaPedidos)) return evaluacionCache;
        Evaluacion e = calcularEvaluacion();
        if (disponibleHasta != null && e.fin().isAfter(disponibleHasta))
            e = new Evaluacion(e.arribos(), e.entregas(), e.fin(), e.distancia(), true);
        pedidosCache = List.copyOf(secuenciaPedidos); evaluacionCache = e;
        return e;
    }

    private Evaluacion calcularEvaluacion() {
        List<LocalDateTime> arribos = new ArrayList<>(), entregas = new ArrayList<>();
        Nodo posicion = pasarPorAlmacenOrigen ? posicionInicio : almacenOrigen.getUbicacion();
        Nodo anterior = nodoAnterior;
        LocalDateTime tiempo = inicio;
        double distancia = 0;
        if (pasarPorAlmacenOrigen && !cargaYaRecogida && !secuenciaPedidos.isEmpty()) {
            var tramo = trayecto(posicion, almacenOrigen.getUbicacion(), anterior, tiempo);
            if (tramo.isEmpty()) return new Evaluacion(arribos, entregas, tiempo, distancia, true);
            var nodos = tramo.get().nodos();
            if (!nodos.isEmpty()) anterior = nodos.size() == 1 ? posicion : nodos.get(nodos.size()-2);
            distancia += nodos.size()*reglas.distanciaNodoKm();
            tiempo = sumarHoras(tramo.get().llegada(), tiempoCargaHoras); posicion = almacenOrigen.getUbicacion();
        }
        for (int i=0; i<secuenciaPedidos.size();) {
            Pedido p = secuenciaPedidos.get(i);
            var tramo = trayecto(posicion, p.getUbicacion(), anterior, tiempo);
            if (tramo.isEmpty()) return new Evaluacion(arribos, entregas, tiempo, distancia, true);
            var nodos = tramo.get().nodos();
            if (!nodos.isEmpty()) anterior = nodos.size()==1 ? posicion : nodos.get(nodos.size()-2);
            distancia += nodos.size()*reglas.distanciaNodoKm();
            tiempo = tramo.get().llegada();
            if (tiempo.isBefore(p.getFechaLlegada())) tiempo = p.getFechaLlegada();
            int finGrupo=i+1;
            while (finGrupo<secuenciaPedidos.size() && mismaParada(p,secuenciaPedidos.get(finGrupo))
                    && !secuenciaPedidos.get(finGrupo).getFechaLlegada().isAfter(tiempo)) finGrupo++;
            LocalDateTime finServicio = sumarHoras(tiempo,reglas.servicioHoras());
            for (int j=i;j<finGrupo;j++) { arribos.add(tiempo); entregas.add(finServicio); }
            posicion=p.getUbicacion(); tiempo=finServicio; i=finGrupo;
        }
        if (reglas.incluirRetorno() && (!secuenciaPedidos.isEmpty() || cargaYaRecogida)) {
            var tramo=trayecto(posicion,almacenRetorno.getUbicacion(),anterior,tiempo);
            if (tramo.isEmpty()) return new Evaluacion(arribos,entregas,tiempo,distancia,true);
            tiempo=tramo.get().llegada(); distancia+=tramo.get().nodos().size()*reglas.distanciaNodoKm();
        }
        return new Evaluacion(arribos,entregas,tiempo,distancia,false);
    }

    private java.util.Optional<DistanciaReticula.Trayecto> trayecto(
            Nodo desde,Nodo hasta,Nodo anterior,LocalDateTime momento) {
        if (pasarPorAlmacenOrigen) return DistanciaReticula.trayectoTemporal(desde,hasta,anterior,momento,
                vehiculo.getVelocidadKmh(),reglas.distanciaNodoKm(),nodosBloqueados,calendarioBloqueos,
                reglas.anchoCiudad(),reglas.altoCiudad());
        var camino=pe.edu.pucp.sisrap.simulacion.aplicacion.CaminoReticula.calcular(
                desde,hasta,nodosBloqueados,reglas.anchoCiudad(),reglas.altoCiudad(),anterior);
        return camino.map(nodos -> new DistanciaReticula.Trayecto(sumarHoras(momento,
                nodos.size()*reglas.distanciaNodoKm()/vehiculo.getVelocidadKmh()),nodos,List.of()));
    }

    private static boolean mismaParada(Pedido a,Pedido b) {
        return java.util.Objects.equals(a.getIdCliente(),b.getIdCliente())
                && a.getUbicacion().getX()==b.getUbicacion().getX()
                && a.getUbicacion().getY()==b.getUbicacion().getY();
    }

    public void recalcular() {
        if (!pasarPorAlmacenOrigen) {
            var ruta = rutaExperimental(); ruta.recalcular();
            intransitable = ruta.isIntransitable(); fueraDeTurno = ruta.isFueraDeTurno();
            distanciaTotalKm = ruta.getDistanciaTotalKm(); costoTotal = ruta.getCostoTotal();
            tiempoTotalHoras = ruta.getTiempoTotalHoras(); return;
        }
        var e=evaluar();
        intransitable=e.inaccesible();
        fueraDeTurno=false;
        distanciaTotalKm=e.distancia();
        costoTotal=distanciaTotalKm*vehiculo.getCostoPorKm();
        tiempoTotalHoras=horasEntre(inicio,e.fin());
    }

    public double horaLlegadaDe(int indice) {
        if (!pasarPorAlmacenOrigen) return rutaExperimental().horaLlegadaDe(indice);
        var e=evaluar();
        return e.arribos().size()>indice ? horasEntre(inicio,e.arribos().get(indice)) : Double.POSITIVE_INFINITY;
    }

    public double horaLlegadaSiAgrega(Pedido nuevo,Almacen origen) {
        if (!pasarPorAlmacenOrigen) return rutaExperimental().horaLlegadaSiAgrega(nuevo, origen);
        Ruta candidata=copiar(); candidata.setAlmacenOrigen(origen);
        candidata.secuenciaPedidos.add(nuevo);
        var e=candidata.evaluar();
        if (e.inaccesible()) return Double.POSITIVE_INFINITY;
        for(int i=0;i<candidata.secuenciaPedidos.size();i++) {
            LocalDateTime arribo=e.arribos().get(i);
            if(arribo.isAfter(candidata.secuenciaPedidos.get(i).getFechaLimite()))
                return Double.POSITIVE_INFINITY;
        }
        return horasEntre(inicio,e.arribos().get(e.arribos().size()-1));
    }

    public double horaEntregaDe(int indice) {
        if (!pasarPorAlmacenOrigen) return rutaExperimental().horaEntregaDe(indice);
        var e=evaluar();
        return e.entregas().size()>indice ? horasEntre(inicio,e.entregas().get(indice)) : Double.POSITIVE_INFINITY;
    }

    public double tiempoAtencionDe(int indice) {
        if (!pasarPorAlmacenOrigen) return rutaExperimental().tiempoAtencionDe(indice);
        var e=evaluar();
        return e.entregas().size()>indice ? Math.max(0,horasEntre(
                secuenciaPedidos.get(indice).getFechaLlegada(),e.entregas().get(indice))) : 1e12;
    }

    public double retrasoDe(int indice) {
        if (!pasarPorAlmacenOrigen) return rutaExperimental().retrasoDe(indice);
        var e=evaluar();
        if(e.arribos().size()<=indice) return 1e12;
        return Math.max(0,horasEntre(secuenciaPedidos.get(indice).getFechaLimite(), e.arribos().get(indice)));
    }

    private LocalDateTime sumarHoras(LocalDateTime base,double horas) {
        return pasarPorAlmacenOrigen ? JornadaOperativa.avanzar(base,horas)
                : base.plusNanos(Math.round(horas*3_600_000_000_000.0));
    }

    private static double horasEntre(LocalDateTime desde,LocalDateTime hasta) {
        return Duration.between(desde,hasta).toNanos()/3_600_000_000_000.0;
    }

    public void continuarConCarga(Nodo anterior) { cargaYaRecogida=true; nodoAnterior=anterior; evaluacionCache=null; }
    public LocalDateTime getInicio() { return inicio; }
    public Vehiculo getVehiculo() { return vehiculo; }
    public Almacen getAlmacenOrigen() { return almacenOrigen; }
    public Almacen getAlmacenRetorno() { return almacenRetorno; }

    /** El retorno puede finalizar en cualquier almacén accesible; no modifica el origen de carga. */
    public void setAlmacenRetorno(Almacen almacenRetorno) {
        if (almacenRetorno == null) throw new IllegalArgumentException("Almacén de retorno inválido");
        this.almacenRetorno = almacenRetorno;
        evaluacionCache = null;
    }
    public Nodo getPosicionInicio() { return posicionInicio; }
    public boolean isPasarPorAlmacenOrigen() { return pasarPorAlmacenOrigen; }

    public void setAlmacenOrigen(Almacen almacenOrigen) {
        if (almacenOrigen == null) {
            throw new IllegalArgumentException("El almacén de origen no puede ser nulo");
        }
        this.almacenOrigen = almacenOrigen;
        this.almacenRetorno = almacenOrigen;
        evaluacionCache = null;
    }

    public void setAlmacenesCandidatos(List<Almacen> almacenes) {
        this.almacenesCandidatos = almacenes == null
                ? List.of()
                : List.copyOf(almacenes);
    }

    public List<Almacen> getAlmacenesCandidatos() { return almacenesCandidatos; }
    public List<Pedido> getSecuenciaPedidos() { return secuenciaPedidos; }
    public double getDistanciaTotalKm() { return distanciaTotalKm; }
    public double getCostoTotal() { return costoTotal; }
    public double getTiempoTotalHoras() { return tiempoTotalHoras; }
    private RutaExperimental rutaExperimental() {
        RutaExperimental ruta = new RutaExperimental(vehiculo, almacenOrigen, posicionInicio,
                false, inicio, reglas);
        ruta.setNodosBloqueados(nodosBloqueados);
        ruta.setAlmacenesCandidatos(almacenesCandidatos);
        ruta.setAlmacenRetorno(almacenRetorno);
        ruta.getSecuenciaPedidos().addAll(secuenciaPedidos);
        return ruta;
    }


private static final class RutaExperimental {

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private Almacen almacenRetorno;
    private final Nodo posicionInicio;
    private final boolean pasarPorAlmacenOrigen;
    private final LocalDateTime inicio;
    private final ReglasPlanificacion reglas;

    private final List<Pedido> secuenciaPedidos = new ArrayList<>();

    private double distanciaTotalKm;
    private double costoTotal;
    private double tiempoTotalHoras;

    private Set<String> nodosBloqueados = Set.of();
    private List<Almacen> almacenesCandidatos = List.of();
    private boolean intransitable;
    private boolean fueraDeTurno;

    /**
     * Constructor histórico: conserva exactamente el comportamiento usado por
     * la experimentación. La ruta se considera iniciada en el almacén origen.
     */
    public RutaExperimental(
            Vehiculo vehiculo,
            Almacen origen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {
        this(
                vehiculo,
                origen,
                origen.getUbicacion(),
                false,
                inicio,
                reglas);
    }

    /**
     * Constructor operativo. Cuando {@code pasarPorAlmacenOrigen=true}, el
     * vehículo parte desde su posición real y primero viaja al almacén elegido
     * por el planificador para recoger la carga.
     */
    public RutaExperimental(
            Vehiculo vehiculo,
            Almacen origen,
            Nodo posicionInicio,
            boolean pasarPorAlmacenOrigen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {

        if (vehiculo == null
                || origen == null
                || posicionInicio == null
                || inicio == null
                || reglas == null) {
            throw new IllegalArgumentException("RutaExperimental incompleta");
        }

        this.vehiculo = vehiculo;
        this.almacenOrigen = origen;
        this.almacenRetorno = origen;
        this.posicionInicio = posicionInicio;
        this.pasarPorAlmacenOrigen = pasarPorAlmacenOrigen;
        this.inicio = inicio;
        this.reglas = reglas;
    }

    public void setNodosBloqueados(Set<String> nodos) {
        this.nodosBloqueados = nodos == null ? Set.of() : nodos;
    }

    public Set<String> getNodosBloqueados() { return nodosBloqueados; }
    public boolean isIntransitable() { return intransitable; }
    public boolean isFueraDeTurno() { return fueraDeTurno; }

    public RutaExperimental copiar() {
        RutaExperimental copia = new RutaExperimental(
                vehiculo,
                almacenOrigen,
                posicionInicio,
                pasarPorAlmacenOrigen,
                inicio,
                reglas);
        copia.nodosBloqueados = nodosBloqueados;
        copia.almacenesCandidatos = almacenesCandidatos;
        copia.almacenRetorno = almacenRetorno;
        copia.secuenciaPedidos.addAll(secuenciaPedidos);
        copia.recalcular();
        return copia;
    }

    public int cargaTotal() {
        return secuenciaPedidos.stream()
                .mapToInt(Pedido::getCantidadQq)
                .sum();
    }

    public void recalcular() {
        intransitable = false;
        fueraDeTurno = false;
        double distanciaPasos = 0.0;
        Nodo posicion = posicionInicialPara(almacenOrigen);
        LocalDateTime reloj = inicio;

        if (pasarPorAlmacenOrigen && !secuenciaPedidos.isEmpty()) {
            int pasosAlmacen = pasosEntre(posicion, almacenOrigen.getUbicacion());
            if (pasosAlmacen < 0) {
                intransitable = true;
                pasosAlmacen = posicion.distanciaManhattan(almacenOrigen.getUbicacion());
            }
            distanciaPasos += pasosAlmacen;
            reloj = sumarHoras(
                    reloj,
                    pasosAlmacen * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            posicion = almacenOrigen.getUbicacion();
        }

        for (Pedido pedido : secuenciaPedidos) {
            int pasos = pasosEntre(posicion, pedido.getUbicacion());
            if (pasos < 0) {
                intransitable = true;
                pasos = posicion.distanciaManhattan(pedido.getUbicacion());
            }

            distanciaPasos += pasos;
            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());

            if (reloj.isBefore(pedido.getFechaLlegada())) {
                reloj = pedido.getFechaLlegada();
            }

            reloj = sumarHoras(reloj, reglas.servicioHoras());
            posicion = pedido.getUbicacion();
        }

        if (reglas.incluirRetorno() && !secuenciaPedidos.isEmpty()) {
            int pasos = pasosEntre(posicion, almacenRetorno.getUbicacion());
            if (pasos < 0) {
                intransitable = true;
                pasos = posicion.distanciaManhattan(almacenRetorno.getUbicacion());
            }
            distanciaPasos += pasos;
            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
        }

        distanciaTotalKm = distanciaPasos * reglas.distanciaNodoKm();
        costoTotal = distanciaTotalKm * vehiculo.getCostoPorKm();
        tiempoTotalHoras = horasEntre(inicio, reloj);
        // El conductor es relevado en el mismo punto al terminar su turno.
        // Solo se impide iniciar una ruta durante la hora de refrigerio.
        fueraDeTurno = pasarPorAlmacenOrigen && !secuenciaPedidos.isEmpty()
                && JornadaOperativa.refrigerio(inicio);
    }

    public double horaLlegadaDe(int indice) {
        return horasEntre(inicio, fechaLlegadaRutaDe(indice));
    }

    public double horaLlegadaSiAgrega(Pedido nuevoPedido, Almacen origenCandidato) {
        if (nuevoPedido == null || origenCandidato == null) {
            throw new IllegalArgumentException("Pedido/origen candidato inválido");
        }

        Nodo posicion = posicionInicialPara(origenCandidato);
        LocalDateTime reloj = inicio;

        if (pasarPorAlmacenOrigen) {
            int pasosAlmacen = pasosEntre(posicion, origenCandidato.getUbicacion());
            if (pasosAlmacen < 0) {
                return Double.POSITIVE_INFINITY;
            }
            reloj = sumarHoras(
                    reloj,
                    pasosAlmacen * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            posicion = origenCandidato.getUbicacion();
        }

        for (Pedido pedido : secuenciaPedidos) {
            int pasos = pasosEntre(posicion, pedido.getUbicacion());
            if (pasos < 0) return Double.POSITIVE_INFINITY;

            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            if (reloj.isBefore(pedido.getFechaLlegada())) {
                reloj = pedido.getFechaLlegada();
            }
            reloj = sumarHoras(reloj, reglas.servicioHoras());
            posicion = pedido.getUbicacion();
        }

        int pasosFinales = pasosEntre(posicion, nuevoPedido.getUbicacion());
        if (pasosFinales < 0) return Double.POSITIVE_INFINITY;

        reloj = sumarHoras(
                reloj,
                pasosFinales * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
        if (reloj.isBefore(nuevoPedido.getFechaLlegada())) {
            reloj = nuevoPedido.getFechaLlegada();
        }

        LocalDateTime fin = sumarHoras(reloj, reglas.servicioHoras());
        if (reglas.incluirRetorno()) {
            int retorno = pasosEntre(nuevoPedido.getUbicacion(), origenCandidato.getUbicacion());
            if (retorno < 0) return Double.POSITIVE_INFINITY;
            fin = sumarHoras(fin, retorno * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
        }
        if (pasarPorAlmacenOrigen && JornadaOperativa.refrigerio(inicio)) {
            return Double.POSITIVE_INFINITY;
        }
        if (reloj.isAfter(nuevoPedido.getFechaLimite())) return Double.POSITIVE_INFINITY;
        return horasEntre(inicio, reloj);
    }

    public double horaEntregaDe(int indice) {
        LocalDateTime entrega = sumarHoras(
                fechaLlegadaRutaDe(indice),
                reglas.servicioHoras());
        return horasEntre(inicio, entrega);
    }

    public double tiempoAtencionDe(int indice) {
        Pedido pedido = secuenciaPedidos.get(indice);
        LocalDateTime entrega = sumarHoras(
                fechaLlegadaRutaDe(indice),
                reglas.servicioHoras());
        return Math.max(0.0, horasEntre(pedido.getFechaLlegada(), entrega));
    }

    public double retrasoDe(int indice) {
        Pedido pedido = secuenciaPedidos.get(indice);
        LocalDateTime momento = reglas.servicioDentroPlazo()
                ? sumarHoras(fechaLlegadaRutaDe(indice), reglas.servicioHoras())
                : fechaLlegadaRutaDe(indice);

        if (!momento.isAfter(pedido.getFechaLimite())) return 0.0;
        return horasEntre(pedido.getFechaLimite(), momento);
    }

    private LocalDateTime fechaLlegadaRutaDe(int indice) {
        if (indice < 0 || indice >= secuenciaPedidos.size()) {
            throw new IndexOutOfBoundsException("Índice de pedido fuera de ruta");
        }

        Nodo posicion = posicionInicialPara(almacenOrigen);
        LocalDateTime reloj = inicio;

        if (pasarPorAlmacenOrigen) {
            int pasosAlmacen = pasosEntre(posicion, almacenOrigen.getUbicacion());
            if (pasosAlmacen < 0) {
                pasosAlmacen = posicion.distanciaManhattan(almacenOrigen.getUbicacion());
            }
            reloj = sumarHoras(
                    reloj,
                    pasosAlmacen * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            posicion = almacenOrigen.getUbicacion();
        }

        for (int i = 0; i <= indice; i++) {
            Pedido pedido = secuenciaPedidos.get(i);
            int pasos = pasosEntre(posicion, pedido.getUbicacion());
            if (pasos < 0) {
                pasos = posicion.distanciaManhattan(pedido.getUbicacion());
            }

            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());

            if (reloj.isBefore(pedido.getFechaLlegada())) {
                reloj = pedido.getFechaLlegada();
            }

            if (i == indice) return reloj;

            reloj = sumarHoras(reloj, reglas.servicioHoras());
            posicion = pedido.getUbicacion();
        }

        throw new IllegalStateException("No se pudo calcular la llegada");
    }

    private Nodo posicionInicialPara(Almacen origen) {
        return pasarPorAlmacenOrigen
                ? posicionInicio
                : origen.getUbicacion();
    }

    private int pasosEntre(Nodo origen, Nodo destino) {
        return DistanciaReticula.distancia(
                origen,
                destino,
                nodosBloqueados,
                reglas.anchoCiudad(),
                reglas.altoCiudad());
    }

    private LocalDateTime sumarHoras(LocalDateTime base, double horas) {
        return pasarPorAlmacenOrigen
                ? JornadaOperativa.avanzar(base, horas)
                : base.plusNanos(Math.round(horas * 3_600_000_000_000.0));
    }

    private static double horasEntre(LocalDateTime desde, LocalDateTime hasta) {
        return Duration.between(desde, hasta).toNanos()
                / 3_600_000_000_000.0;
    }

    public Vehiculo getVehiculo() { return vehiculo; }
    public Almacen getAlmacenOrigen() { return almacenOrigen; }
    public Almacen getAlmacenRetorno() { return almacenRetorno; }

    /** El retorno puede finalizar en cualquier almacén accesible; no modifica el origen de carga. */
    public void setAlmacenRetorno(Almacen almacenRetorno) {
        if (almacenRetorno == null) throw new IllegalArgumentException("Almacén de retorno inválido");
        this.almacenRetorno = almacenRetorno;
    }
    public Nodo getPosicionInicio() { return posicionInicio; }
    public boolean isPasarPorAlmacenOrigen() { return pasarPorAlmacenOrigen; }

    public void setAlmacenOrigen(Almacen almacenOrigen) {
        if (almacenOrigen == null) {
            throw new IllegalArgumentException("El almacén de origen no puede ser nulo");
        }
        this.almacenOrigen = almacenOrigen;
        this.almacenRetorno = almacenOrigen;
    }

    public void setAlmacenesCandidatos(List<Almacen> almacenes) {
        this.almacenesCandidatos = almacenes == null
                ? List.of()
                : List.copyOf(almacenes);
    }

    public List<Almacen> getAlmacenesCandidatos() { return almacenesCandidatos; }
    public List<Pedido> getSecuenciaPedidos() { return secuenciaPedidos; }
    public double getDistanciaTotalKm() { return distanciaTotalKm; }
    public double getCostoTotal() { return costoTotal; }
    public double getTiempoTotalHoras() { return tiempoTotalHoras; }
}

}
