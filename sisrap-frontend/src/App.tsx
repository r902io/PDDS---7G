import { SimulacionProvider, useSimulacion } from './estado/SimulacionContext';
import { useVista } from './estado/navegacion';
import { Cabecera } from './components/Cabecera';
import { SelectorEscenario } from './pages/SelectorEscenario';
import { EscenarioDashboard } from './pages/EscenarioDashboard';
import { ResultadoCorrida } from './pages/ResultadoCorrida';
import { Mantenimiento } from './pages/Mantenimiento';
import { ErrorCarga } from './components/ui/Avisos';

function Aplicacion() {
  const vista = useVista();
  const { errorEstado, snapshot, conexion } = useSimulacion();

  return (
    <div className="h-dvh flex flex-col bg-bg text-texto overflow-hidden">
      <Cabecera vista={vista} />

      {/* Backend inalcanzable: se informa en todas las vistas, sin datos de respaldo. */}
      {errorEstado && !snapshot && conexion !== 'ABIERTA' && vista.nombre !== 'operacion' && vista.nombre !== 'resultado' && (
        <div className="px-4 pt-3">
          <ErrorCarga titulo="Sin conexión con el backend de SisRap" error={errorEstado} reintentar={() => window.location.reload()} />
        </div>
      )}

      {vista.nombre === 'selector' && <SelectorEscenario />}
      {vista.nombre === 'operacion' && <EscenarioDashboard />}
      {vista.nombre === 'resultado' && <ResultadoCorrida />}
      {vista.nombre === 'mantenimiento' && <Mantenimiento pestana={vista.pestana} />}
    </div>
  );
}

export default function App() {
  return (
    <SimulacionProvider>
      <Aplicacion />
    </SimulacionProvider>
  );
}
