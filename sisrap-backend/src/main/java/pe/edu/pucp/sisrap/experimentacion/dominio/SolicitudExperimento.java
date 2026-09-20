package pe.edu.pucp.sisrap.experimentacion.dominio;
import java.time.LocalDateTime;
public record SolicitudExperimento(String perfil,String fase,LocalDateTime desde,LocalDateTime instante) {
    public SolicitudExperimento {
        if(perfil==null || !perfil.matches("[A-Za-z0-9_-]{1,60}"))throw new IllegalArgumentException("Perfil inválido");
        if(!"CALIBRACION".equals(fase) && !"COMPARACION".equals(fase))throw new IllegalArgumentException("Fase inválida");
        if(desde==null || instante==null || !desde.isBefore(instante))throw new IllegalArgumentException("Intervalo inválido");
    }
}
