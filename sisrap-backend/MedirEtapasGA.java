import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import javax.tools.ToolProvider;

/** Ejecutar desde sisrap-backend con JDK 21: java MedirEtapasGA.java [ventas202610.txt|demo] [presupuestoMs] */
public class MedirEtapasGA {
 static String cambiar(String texto,String original,String nuevo) {
  int inicio=texto.indexOf(original);
  if(inicio<0 || texto.indexOf(original,inicio+original.length())>=0)
   throw new IllegalStateException("La versión del GA no coincide con esta prueba. No se modificó el proyecto.");
  return texto.substring(0,inicio)+nuevo+texto.substring(inicio+original.length());
 }
 public static void main(String[] args)throws Exception {
  var compilador=ToolProvider.getSystemJavaCompiler();
  if(compilador==null || Runtime.version().feature()<21)throw new IllegalStateException("Usa JDK 21 o superior con javac.");
  Path fuentes=Path.of("src/main/java").toAbsolutePath();
  Path original=fuentes.resolve("pe/edu/pucp/sisrap/planificador/dominio/algoritmo/AlgoritmoGenetico.java");
  if(!Files.isRegularFile(original))throw new IllegalArgumentException("Ejecuta dentro de sisrap-backend.");
  String ga=Files.readString(original).replace("\r\n","\n");
  ga=cambiar(ga,"if (mejorOperativo == null || comparar(individuo, mejorOperativo) < 0)\n            mejorOperativo = individuo.copiar();",
   "long inicioSeleccion = System.nanoTime();\n        if (mejorOperativo == null || comparar(individuo, mejorOperativo) < 0)\n            mejorOperativo = individuo.copiar();\n        PruebaEtapasGA.seleccionar(System.nanoTime()-inicioSeleccion);");
  String bucle="for (int generacion = 1; generacion <= parametros.numGeneraciones && quedaTiempo(); generacion++) {";
  ga=cambiar(ga,bucle,bucle+"\n            long inicioGeneracion = System.nanoTime();");
  String fin="traza.add(new PuntoConvergencia(generacion, evaluaciones, mejorOperativo.getFitness()));";
  ga=cambiar(ga,fin,fin+"\n            PruebaEtapasGA.generacion(System.nanoTime()-inicioGeneracion, nueva.size() == parametros.tamanioPoblacion);");
  Path temporal=Files.createTempDirectory("sisrap-medicion-ga-");
  try {
   Path gaTemporal=temporal.resolve("AlgoritmoGenetico.java"),prueba=temporal.resolve("PruebaEtapasGA.java"),clases=temporal.resolve("clases");
   Files.createDirectories(clases);Files.writeString(gaTemporal,ga);
   String fuentePrueba="""
package pe.edu.pucp.sisrap.planificador.dominio.algoritmo;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import pe.edu.pucp.sisrap.parametros.dominio.*;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.pedido.dominio.*;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.*;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;
public class PruebaEtapasGA {
 static class Estadistica {
  long n; double total; long min=Long.MAX_VALUE,max;
  void agregar(long ns){n++;total+=ns;min=Math.min(min,ns);max=Math.max(max,ns);}
  String texto(){return n==0?"N/D (sin muestras)":String.format(Locale.ROOT,"n=%d; media=%.6f ms; mínimo=%.6f ms; máximo=%.6f ms; total=%.6f ms",n,total/n/1e6,min/1e6,max/1e6,total/1e6);}
 }
 static final Estadistica generaciones=new Estadistica(),parciales=new Estadistica(),seleccion=new Estadistica();
 public static void generacion(long ns,boolean completa){(completa?generaciones:parciales).agregar(ns);}
 public static void seleccionar(long ns){seleccion.agregar(ns);}
 public static void main(String[] args)throws Exception {
  long presupuesto=args.length>1?Long.parseLong(args[1]):1000;
  var fecha=LocalDateTime.of(2026,1,1,7,0);
  List<Pedido> pedidos=new ArrayList<>();
  String datos;
  if(args.length>0 && !args[0].equals("demo")) {
   Path archivo=Path.of(args[0]);
   var periodo=java.util.regex.Pattern.compile("(20\\\\d{2})(0[1-9]|1[0-2])").matcher(archivo.getFileName().toString());
   if(!periodo.find())throw new IllegalArgumentException("El nombre debe incluir AAAAMM, por ejemplo ventas202610.txt");
   var mes=YearMonth.of(Integer.parseInt(periodo.group(1)),Integer.parseInt(periodo.group(2)));
   var formato=java.util.regex.Pattern.compile("(\\\\d{2})d(\\\\d{2})h(\\\\d{2})m:(\\\\d+),(\\\\d+),([^,]+),(\\\\d+),(\\\\d+)");
   for(String texto:Files.readAllLines(archivo)) {
    if(texto.isBlank())continue;var m=formato.matcher(texto.strip());
    if(!m.matches())throw new IllegalArgumentException("Registro inválido: "+texto);
    int x=Integer.parseInt(m.group(4)),y=Integer.parseInt(m.group(5));
    if(x>70||y>50)throw new IllegalArgumentException("Coordenadas fuera del mapa 70x50");
    var llegada=mes.atDay(Integer.parseInt(m.group(1))).atTime(Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
    pedidos.add(new Pedido((long)pedidos.size()+1,m.group(6),Integer.parseInt(m.group(7)),TipoPrioridad.REGULAR_36H,new Nodo(x,y),llegada,Integer.parseInt(m.group(8))));
    if(pedidos.size()==25)break;
   }
   if(pedidos.isEmpty())throw new IllegalArgumentException("El archivo no contiene pedidos");
   fecha=pedidos.stream().map(Pedido::getFechaLlegada).max(Comparator.naturalOrder()).orElseThrow();
   datos="Primeros "+pedidos.size()+" pedidos de "+archivo.toAbsolutePath();
  } else {
   Random random=new Random(42);
   for(int i=0;i<25;i++)pedidos.add(new Pedido((long)i+1,"C"+i,1+random.nextInt(4),TipoPrioridad.REGULAR_36H,new Nodo(random.nextInt(71),random.nextInt(51)),fecha,36));
   datos="Caso sintético reproducible: 25 pedidos, semilla 42. No es una corrida productiva.";
  }
  var almacenes=List.of(new Almacen("CENTRAL",new Nodo(27,14),null,null),new Almacen("NOROESTE",new Nodo(12,38),1000,1000),new Almacen("ESTE",new Nodo(57,27),1000,1000));
  List<Vehiculo> flota=new ArrayList<>();
  for(int i=1;i<=10;i++)flota.add(new Vehiculo("TA"+i,24,40,8,new Nodo(27,14)));
  for(int i=1;i<=15;i++)flota.add(new Vehiculo("TM"+i,8,25,6,new Nodo(27,14)));
  for(int i=1;i<=12;i++)flota.add(new Vehiculo("TB"+i,4,12,3,new Nodo(27,14)));
  Map<String,String> valores=new HashMap<>();
  String[][] entradas={{"ga.poblacion","12"},{"ga.generaciones","14"},{"ga.elitismo","1"},{"ga.torneo","2"},{"ga.cruzamiento","0.85"},{"ga.mutacion","0.15"},{"ga.proporcionVoraz","0.3"},{"sa.temperaturaInicial","1000"},{"sa.temperaturaFinal","1"},{"sa.enfriamiento","0.95"},{"sa.recalentamiento","1.2"},{"sa.iteraciones","10"},{"sa.muestras","10"},{"sa.maxNiveles","100"},{"sa.aceptacion","0.8"},{"sa.calibrar","false"},{"busqueda.movimientos","DOS_OPT,SWAP,RELOCATE,CROSS_ROUTE,VEHICLE_CHANGE"},{"objetivo.beta1","100"},{"objetivo.beta2","5000"},{"objetivo.beta3","10000"}};
  for(var entrada:entradas)valores.put(entrada[0],entrada[1]);
  var config=new Configuracion(valores);
  var contexto=new ContextoPlanificacion(pedidos,flota,almacenes,fecha,new ReglasPlanificacion(1,1,false,true,70,50),Set.of(),true);
  var ga=new AlgoritmoGenetico(new ParametrosAlgoritmo(config,42),new FuncionObjetivo(config,contexto),presupuesto);
  long inicio=System.nanoTime();Solucion solucion=ga.planificar(contexto);double ta=(System.nanoTime()-inicio)/1e6;
  String informe="MEDICIÓN LOCAL DE ETAPAS DEL GA\\n"+
   "Interpretación provisional de Sa/Sc indicada por el usuario; confirmar los nombres con el docente.\\n"+
   "Son tiempos de cálculo; no saltos del reloj de simulación.\\nDatos: "+datos+"\\nInstante: "+fecha+"\\n"+
   "Mapa 70x50; flota 10 autos, 15 motos, 12 bicicletas; sin bloqueos ni mantenimientos.\\n"+
   "Población=12; generaciones máximas=14; presupuesto cooperativo="+presupuesto+" ms; semilla GA=42.\\n"+
   "Configuración de prueba: "+new TreeMap<>(valores)+"\\n\\n"+
   String.format(Locale.ROOT,"Ta (planificar completo, incluida población inicial): %.6f ms\\n",ta)+
   "Sa propuesto (construir y evaluar siguiente población): "+generaciones.texto()+"\\n"+
   "Generaciones parciales por presupuesto: "+parciales.texto()+"\\n"+
   "Sc propuesto (comparar candidato con mejor conservado y actualizarlo): "+seleccion.texto()+"\\n\\n"+
   "Sc es trabajo incluido en Ta y en la evolución; no sumar Ta+Sa+Sc.\\n"+
   "Si el presupuesto impide completar una generación se informa N/D en Sa.\\n"+
   "La población inicial está incluida en Ta; se excluye de Sa.\\n"+
   "Milisegundos es la unidad; una etapa puede durar más de 1000 ms.\\n"+
   "Prueba local única: incluye calentamiento de JVM y coste de instrumentación; no mide la VM.\\n"+
   "Rutas resultantes: "+solucion.getRutas().size()+"\\n";
  Path salida=Files.createTempFile(Path.of("."),"medicion-etapas-ga-",".txt");Files.writeString(salida,informe);
  System.out.println(informe);System.out.println("Archivo: "+salida.toAbsolutePath().normalize());
 }
}
""";
   Files.writeString(prueba,fuentePrueba);
   int resultado=compilador.run(null,null,null,"-encoding","UTF-8","--release","21","-sourcepath",fuentes.toString(),"-d",clases.toString(),gaTemporal.toString(),prueba.toString());
   if(resultado!=0)throw new IllegalStateException("No se pudo compilar la prueba; revisa los errores anteriores.");
   try(var cargador=new URLClassLoader(new java.net.URL[]{clases.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
    try{cargador.loadClass("pe.edu.pucp.sisrap.planificador.dominio.algoritmo.PruebaEtapasGA").getMethod("main",String[].class).invoke(null,(Object)args);}
    catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException("Falló la prueba: "+e.getCause().getMessage(),e.getCause());}
   }
  } finally {
   try(var archivos=Files.walk(temporal)){for(Path p:archivos.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  }
 }
}
