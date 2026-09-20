package pe.edu.pucp.sisrap.carga.aplicacion;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import pe.edu.pucp.sisrap.carga.dominio.*;
public final class ImportarPedidos {
    private static final Pattern FORMATO=Pattern.compile("(\\d{2})d(\\d{2})h(\\d{2})m:(\\d+),(\\d+),([^,]+),(\\d+),(\\d+)");
    private final RepositorioCarga repositorio;
    private final int maxCaracteres;
    public ImportarPedidos(RepositorioCarga r,int maxCaracteres){repositorio=r;this.maxCaracteres=maxCaracteres;}
    public record Resultado(int filas,boolean insertado,String huella) {}
    public Resultado ejecutar(String perfil,int anio,int mes,String texto){
        if(texto==null || texto.isBlank() || texto.length()>maxCaracteres)throw new IllegalArgumentException("Archivo vacío o demasiado grande");
        YearMonth periodo=YearMonth.of(anio,mes);
        String normalizado=texto.replace("\uFEFF","").replace("\r\n","\n").strip();
        ReglasCarga reglas=repositorio.reglas(perfil);
        List<PedidoImportado> pedidos=parsear(periodo,normalizado,reglas);
        String huella;
        try{huella=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((periodo+"\n"+normalizado).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        boolean insertado=repositorio.guardar(huella,anio,mes,pedidos);
        return new Resultado(pedidos.size(),insertado,huella);
    }
    public static List<PedidoImportado> parsear(YearMonth periodo,String texto,ReglasCarga reglas){
        List<PedidoImportado> salida=new ArrayList<>();int linea=0;
        for(String fila:texto.split("\\R")){
            linea++;if(fila.isBlank())continue;
            Matcher m=FORMATO.matcher(fila.strip());
            if(!m.matches())throw new IllegalArgumentException("Formato inválido en línea "+linea);
            try{
                int dia=Integer.parseInt(m.group(1)),hora=Integer.parseInt(m.group(2)),min=Integer.parseInt(m.group(3)),
                    x=Integer.parseInt(m.group(4)),y=Integer.parseInt(m.group(5)),cantidad=Integer.parseInt(m.group(7)),plazo=Integer.parseInt(m.group(8));
                var prioridad=reglas.prioridades().get(plazo);
                if(x<0 || x>reglas.ancho() || y<0 || y>reglas.alto() || cantidad<=0 || prioridad==null || m.group(6).length()>20)
                    throw new IllegalArgumentException("Valor fuera de rango");
                salida.add(new PedidoImportado(m.group(6),cantidad,prioridad,plazo,periodo.atDay(dia).atTime(hora,min),x,y));
            }catch(RuntimeException e){throw new IllegalArgumentException("Datos inválidos en línea "+linea+": "+e.getMessage(),e);}
        }
        if(salida.isEmpty())throw new IllegalArgumentException("Archivo sin pedidos");
        return List.copyOf(salida);
    }
}
