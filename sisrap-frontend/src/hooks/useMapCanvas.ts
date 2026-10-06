import { useEffect, useRef, useState, useCallback } from 'react';
import {
  Coordinate,
  Vehicle,
  Warehouse,
  Order,
  RoadBlock,
  Incident,
} from '../types/logistics';

export interface MapViewport {
  offsetX: number;
  offsetY: number;
  scale: number;
}

export function useMapCanvas(
  warehouses: Warehouse[],
  vehicles: Vehicle[],
  orders: Order[],
  roadBlocks: RoadBlock[],
  selectedVehicleId: string | null,
  onSelectVehicle: (id: string | null) => void,
  onSelectOrder: (order: Order | null) => void
) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [viewport, setViewport] = useState<MapViewport>({ offsetX: 40, offsetY: 40, scale: 12 });
  const [hoveredCoord, setHoveredCoord] = useState<Coordinate | null>(null);
  const [hoveredVehicle, setHoveredVehicle] = useState<Vehicle | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 });

  const GRID_WIDTH_KM = 70;
  const GRID_HEIGHT_KM = 50;

  // Convert logical coordinates (0..70, 0..50) where (0,0) is bottom-left
  // to canvas pixel coordinates
  const toCanvasPixels = useCallback(
    (coord: Coordinate, h: number): { px: number; py: number } => {
      const px = viewport.offsetX + coord.x * viewport.scale;
      // Invert Y axis: Y=0 is bottom, Y=50 is top
      const py = h - viewport.offsetY - coord.y * viewport.scale;
      return { px, py };
    },
    [viewport]
  );

  // Convert canvas pixel coordinates to nearest logical grid coordinate
  const toGridCoord = useCallback(
    (px: number, py: number, h: number): Coordinate => {
      const gx = Math.round((px - viewport.offsetX) / viewport.scale);
      const gy = Math.round((h - viewport.offsetY - py) / viewport.scale);
      return {
        x: Math.max(0, Math.min(GRID_WIDTH_KM, gx)),
        y: Math.max(0, Math.min(GRID_HEIGHT_KM, gy)),
      };
    },
    [viewport]
  );

  // Auto-fit function
  const fitToView = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const parent = canvas.parentElement;
    if (!parent) return;

    const w = parent.clientWidth;
    const h = parent.clientHeight;

    const padding = 60;
    const availableW = w - padding * 2;
    const availableH = h - padding * 2;

    const scaleX = availableW / GRID_WIDTH_KM;
    const scaleY = availableH / GRID_HEIGHT_KM;
    const scale = Math.max(6, Math.min(scaleX, scaleY));

    const totalGridPixelW = GRID_WIDTH_KM * scale;
    const totalGridPixelH = GRID_HEIGHT_KM * scale;

    const offsetX = (w - totalGridPixelW) / 2;
    const offsetY = (h - totalGridPixelH) / 2;

    setViewport({ offsetX, offsetY, scale });
  }, []);

  // Window resize observer
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !canvas.parentElement) return;

    fitToView();

    const resizeObserver = new ResizeObserver(() => {
      fitToView();
    });

    resizeObserver.observe(canvas.parentElement);
    return () => resizeObserver.disconnect();
  }, [fitToView]);

  // Main Canvas Render
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    const w = rect.width;
    const h = rect.height;

    canvas.width = w * dpr;
    canvas.height = h * dpr;
    ctx.scale(dpr, dpr);

    // 1. Clear background
    ctx.fillStyle = '#0a111e'; // --color-bg
    ctx.fillRect(0, 0, w, h);

    // 2. Draw 70x50 km Manhattan Grid Lines
    ctx.lineWidth = 1;
    ctx.strokeStyle = '#16233a'; // --color-panel2

    // Vertical lines (X: 0 to 70)
    for (let x = 0; x <= GRID_WIDTH_KM; x++) {
      const p1 = toCanvasPixels({ x, y: 0 }, h);
      const p2 = toCanvasPixels({ x, y: GRID_HEIGHT_KM }, h);

      ctx.beginPath();
      ctx.moveTo(p1.px, p1.py);
      ctx.lineTo(p2.px, p2.py);
      ctx.strokeStyle = x % 10 === 0 ? '#24364f' : '#141e30';
      ctx.stroke();

      // Axis label every 10 km
      if (x % 10 === 0) {
        ctx.fillStyle = '#a7b6c9';
        ctx.font = '10px ui-monospace, monospace';
        ctx.textAlign = 'center';
        ctx.fillText(`${x}`, p1.px, p1.py + 15);
      }
    }

    // Horizontal lines (Y: 0 to 50)
    for (let y = 0; y <= GRID_HEIGHT_KM; y++) {
      const p1 = toCanvasPixels({ x: 0, y }, h);
      const p2 = toCanvasPixels({ x: GRID_WIDTH_KM, y }, h);

      ctx.beginPath();
      ctx.moveTo(p1.px, p1.py);
      ctx.lineTo(p2.px, p2.py);
      ctx.strokeStyle = y % 10 === 0 ? '#24364f' : '#141e30';
      ctx.stroke();

      // Axis label every 10 km
      if (y % 10 === 0) {
        ctx.fillStyle = '#a7b6c9';
        ctx.font = '10px ui-monospace, monospace';
        ctx.textAlign = 'right';
        ctx.fillText(`${y}`, p1.px - 6, p1.py + 3);
      }
    }

    // Grid boundary outline
    const b00 = toCanvasPixels({ x: 0, y: 0 }, h);
    const b70_50 = toCanvasPixels({ x: GRID_WIDTH_KM, y: GRID_HEIGHT_KM }, h);
    ctx.strokeStyle = '#24364f';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(b00.px, b70_50.py, b70_50.px - b00.px, b00.py - b70_50.py);

    // 3. Draw Road Blocks (Red thick dashed line with prohibition mark)
    roadBlocks.forEach((block) => {
      const p1 = toCanvasPixels(block.from, h);
      const p2 = toCanvasPixels(block.to, h);

      ctx.save();
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 3;
      ctx.setLineDash([5, 3]);
      ctx.beginPath();
      ctx.moveTo(p1.px, p1.py);
      ctx.lineTo(p2.px, p2.py);
      ctx.stroke();

      // Prohibition badge on midpoint
      const midX = (p1.px + p2.px) / 2;
      const midY = (p1.py + p2.py) / 2;
      ctx.fillStyle = '#ef4444';
      ctx.beginPath();
      ctx.arc(midX, midY, 6, 0, Math.PI * 2);
      ctx.fill();

      ctx.strokeStyle = '#0a111e';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([]);
      ctx.beginPath();
      ctx.moveTo(midX - 3, midY - 3);
      ctx.lineTo(midX + 3, midY + 3);
      ctx.stroke();
      ctx.restore();
    });

    // 4. Draw Vehicle Routes (Trajectories)
    vehicles.forEach((vehicle) => {
      const isSelected = vehicle.id === selectedVehicleId;
      const isHovered = hoveredVehicle?.id === vehicle.id;
      const highlight = isSelected || isHovered;

      // Color token
      let strokeColor = '#60a5fa'; // Auto: azul
      if (vehicle.type === 'MOTO') strokeColor = '#a78bfa'; // Moto: violeta
      if (vehicle.type === 'BICICLETA') strokeColor = '#34d399'; // Bici: mint

      const opacity = highlight ? 1.0 : selectedVehicleId ? 0.2 : 0.7;

      ctx.save();
      // Draw traveled path (faint dotted)
      if (vehicle.traveledPath.length > 0) {
        ctx.strokeStyle = strokeColor;
        ctx.globalAlpha = opacity * 0.35;
        ctx.lineWidth = 1.5;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        vehicle.traveledPath.forEach((pt, idx) => {
          const cp = toCanvasPixels(pt, h);
          if (idx === 0) ctx.moveTo(cp.px, cp.py);
          else ctx.lineTo(cp.px, cp.py);
        });
        const currentP = toCanvasPixels(vehicle.coord, h);
        ctx.lineTo(currentP.px, currentP.py);
        ctx.stroke();
      }

      // Draw pending path (solid line, strictly orthogonal Manhattan)
      if (vehicle.pendingPath.length > 0) {
        ctx.globalAlpha = opacity;
        ctx.lineWidth = highlight ? 2.5 : 1.5;
        ctx.setLineDash([]);

        // Amber halo if alternative route around block
        if (vehicle.hasAlternativeRoute) {
          ctx.strokeStyle = '#fbbf24';
          ctx.lineWidth = 4.5;
          ctx.beginPath();
          const startP = toCanvasPixels(vehicle.coord, h);
          ctx.moveTo(startP.px, startP.py);
          vehicle.pendingPath.forEach((pt) => {
            const cp = toCanvasPixels(pt, h);
            ctx.lineTo(cp.px, cp.py);
          });
          ctx.stroke();
        }

        ctx.strokeStyle = strokeColor;
        ctx.beginPath();
        const startP = toCanvasPixels(vehicle.coord, h);
        ctx.moveTo(startP.px, startP.py);
        vehicle.pendingPath.forEach((pt) => {
          const cp = toCanvasPixels(pt, h);
          ctx.lineTo(cp.px, cp.py);
        });
        ctx.stroke();

        // Destination marker
        if (vehicle.currentTargetCoord) {
          const destP = toCanvasPixels(vehicle.currentTargetCoord, h);
          ctx.fillStyle = vehicle.destinationType === 'CLIENTE' ? '#fbbf24' : '#34d399';
          ctx.beginPath();
          ctx.arc(destP.px, destP.py, 4, 0, Math.PI * 2);
          ctx.fill();
        }
      }
      ctx.restore();
    });

    // 5. Draw Client Orders (Circles colored by Semáforo)
    orders.forEach((order) => {
      if (order.status === 'ENTREGADO') return;

      const p = toCanvasPixels(order.coord, h);
      let color = '#22c55e'; // Verde
      if (order.criticality === 'AMBAR') color = '#f59e0b';
      if (order.criticality === 'ROJO') color = '#ef4444';

      ctx.save();
      // Outer glow for critical
      if (order.criticality === 'ROJO') {
        ctx.fillStyle = 'rgba(239, 68, 68, 0.25)';
        ctx.beginPath();
        ctx.arc(p.px, p.py, 10, 0, Math.PI * 2);
        ctx.fill();
      }

      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.arc(p.px, p.py, 5, 0, Math.PI * 2);
      ctx.fill();

      ctx.strokeStyle = '#0a111e';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Client label
      ctx.fillStyle = '#e8eef7';
      ctx.font = '9px system-ui, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(order.clientCode, p.px, p.py - 7);
      ctx.restore();
    });

    // 6. Draw Warehouses: Centered letters AC, A1, A2 inside pins (NEVER red!)
    warehouses.forEach((wh) => {
      const p = toCanvasPixels(wh.coord, h);
      const isCentral = wh.isCentral;
      const size = isCentral ? 22 : 20;

      ctx.save();
      if (isCentral) {
        // Almacén Central (AC): Cian / Blanco destacado con letras 'AC' dentro (PROHIBIDO ROJO)
        // Halo
        ctx.fillStyle = 'rgba(56, 189, 248, 0.35)';
        ctx.fillRect(p.px - size * 0.75, p.py - size * 0.75, size * 1.5, size * 1.5);

        // Pin square
        ctx.fillStyle = '#38bdf8'; // Cian
        ctx.fillRect(p.px - size / 2, p.py - size / 2, size, size);

        // White border
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.strokeRect(p.px - size / 2, p.py - size / 2, size, size);

        // Center letters 'AC' inside pin
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 11px ui-monospace, monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('AC', p.px, p.py);

        // Label below
        ctx.textBaseline = 'alphabetic';
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 11px system-ui, sans-serif';
        ctx.fillText(`AC (${wh.coord.x},${wh.coord.y}) ∞`, p.px, p.py + size / 2 + 13);
      } else {
        // Almacenes Intermedios (A1, A2): Mint con letras 'A1' y 'A2' dentro
        ctx.fillStyle = 'rgba(52, 211, 153, 0.25)';
        ctx.fillRect(p.px - size * 0.75, p.py - size * 0.75, size * 1.5, size * 1.5);

        ctx.fillStyle = '#34d399'; // Mint
        ctx.fillRect(p.px - size / 2, p.py - size / 2, size, size);

        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1.5;
        ctx.strokeRect(p.px - size / 2, p.py - size / 2, size, size);

        // Center letters 'A1' or 'A2' inside pin
        ctx.fillStyle = '#0a111e';
        ctx.font = 'bold 10px ui-monospace, monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(wh.code, p.px, p.py);

        // Label below
        ctx.textBaseline = 'alphabetic';
        ctx.fillStyle = '#34d399';
        ctx.font = 'bold 10px system-ui, sans-serif';
        ctx.fillText(`${wh.code} (${wh.coord.x},${wh.coord.y}) [${wh.currentStock}]`, p.px, p.py + size / 2 + 12);
      }
      ctx.restore();
    });

    // 7. Draw Vehicles: Solid colored points without any automatic numbers inside
    const nodeGroups: Record<string, Vehicle[]> = {};
    vehicles.forEach((v) => {
      const key = `${v.coord.x},${v.coord.y}`;
      if (!nodeGroups[key]) nodeGroups[key] = [];
      nodeGroups[key].push(v);
    });

    Object.entries(nodeGroups).forEach(([key, group]) => {
      const [gx, gy] = key.split(',').map(Number);
      const isWarehouseNode = warehouses.some((w) => w.coord.x === gx && w.coord.y === gy);

      // If vehicles are stationed at a warehouse, the warehouse marker already represents the node
      if (isWarehouseNode) {
        return;
      }

      const p = toCanvasPixels({ x: gx, y: gy }, h);
      const v = group[0];
      const isSelected = group.some((veh) => veh.id === selectedVehicleId);

      let vColor = '#60a5fa'; // Auto: Azul
      if (v.type === 'MOTO') vColor = '#a78bfa'; // Moto: Violeta
      if (v.type === 'BICICLETA') vColor = '#34d399'; // Bici: Mint

      ctx.save();
      // Selection ring
      if (isSelected) {
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(p.px, p.py, 10, 0, Math.PI * 2);
        ctx.stroke();
      }

      // Solid color point (NO automatic numbers rendered inside)
      ctx.fillStyle = v.status === 'AVERIADO' ? '#ef4444' : vColor;
      ctx.beginPath();
      ctx.arc(p.px, p.py, 6, 0, Math.PI * 2);
      ctx.fill();

      ctx.strokeStyle = '#0a111e';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Status badges if Breakdown or Lunch Break
      if (v.status === 'AVERIADO') {
        ctx.fillStyle = '#ef4444';
        ctx.font = '9px system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('⚠ AVERÍA', p.px, p.py - 10);
      } else if (v.isTakingBreak) {
        ctx.fillStyle = '#fbbf24';
        ctx.font = '9px system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('☕ REFRIGERIO', p.px, p.py - 10);
      }
      ctx.restore();
    });

    // 8. Draw Scale and Cursor Coordinates at Bottom
    ctx.save();
    // Graphic scale bar
    const scaleBarKm = 10;
    const scaleBarPx = scaleBarKm * viewport.scale;
    const barX = 20;
    const barY = h - 25;

    ctx.strokeStyle = '#a7b6c9';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(barX, barY);
    ctx.lineTo(barX + scaleBarPx, barY);
    ctx.moveTo(barX, barY - 4);
    ctx.lineTo(barX, barY + 4);
    ctx.moveTo(barX + scaleBarPx, barY - 4);
    ctx.lineTo(barX + scaleBarPx, barY + 4);
    ctx.stroke();

    ctx.fillStyle = '#a7b6c9';
    ctx.font = '10px ui-monospace, monospace';
    ctx.textAlign = 'left';
    ctx.fillText(`${scaleBarKm} km`, barX + scaleBarPx + 8, barY + 3);

    // Cursor coordinates
    if (hoveredCoord) {
      ctx.textAlign = 'right';
      ctx.fillText(`Pos: (${hoveredCoord.x}, ${hoveredCoord.y})`, w - 20, barY + 3);
    }
    ctx.restore();
  }, [
    viewport,
    warehouses,
    vehicles,
    orders,
    roadBlocks,
    selectedVehicleId,
    hoveredVehicle,
    hoveredCoord,
    toCanvasPixels,
  ]);

  // Mouse / Touch handlers for independent pan and zoom
  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    setIsDragging(true);
    setDragStart({ x: e.clientX - viewport.offsetX, y: e.clientY - viewport.offsetY });
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;

    const gridCoord = toGridCoord(px, py, rect.height);
    setHoveredCoord(gridCoord);

    if (isDragging) {
      setViewport((prev) => ({
        ...prev,
        offsetX: e.clientX - dragStart.x,
        offsetY: e.clientY - dragStart.y,
      }));
    } else {
      // Find hovered vehicle
      const vFound = vehicles.find(
        (v) => Math.abs(v.coord.x - gridCoord.x) <= 1 && Math.abs(v.coord.y - gridCoord.y) <= 1
      );
      setHoveredVehicle(vFound || null);
    }
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  const handleClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const gridCoord = toGridCoord(px, py, rect.height);

    // Check if clicked vehicle
    const clickedVehicle = vehicles.find(
      (v) => Math.abs(v.coord.x - gridCoord.x) <= 1 && Math.abs(v.coord.y - gridCoord.y) <= 1
    );

    if (clickedVehicle) {
      onSelectVehicle(clickedVehicle.id === selectedVehicleId ? null : clickedVehicle.id);
      return;
    }

    // Check if clicked order
    const clickedOrder = orders.find(
      (o) => o.coord.x === gridCoord.x && o.coord.y === gridCoord.y && o.status !== 'ENTREGADO'
    );
    if (clickedOrder) {
      onSelectOrder(clickedOrder);
      return;
    }

    // Deselect
    onSelectVehicle(null);
    onSelectOrder(null);
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.15 : 0.85;
    const newScale = Math.max(5, Math.min(45, viewport.scale * zoomFactor));

    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    // Zoom centered on mouse position
    const newOffsetX = mouseX - (mouseX - viewport.offsetX) * (newScale / viewport.scale);
    const newOffsetY = mouseY - (mouseY - viewport.offsetY) * (newScale / viewport.scale);

    setViewport({
      offsetX: newOffsetX,
      offsetY: newOffsetY,
      scale: newScale,
    });
  };

  return {
    canvasRef,
    hoveredCoord,
    hoveredVehicle,
    fitToView,
    handlers: {
      onMouseDown: handleMouseDown,
      onMouseMove: handleMouseMove,
      onMouseUp: handleMouseUp,
      onMouseLeave: () => {
        setIsDragging(false);
        setHoveredCoord(null);
        setHoveredVehicle(null);
      },
      onClick: handleClick,
      onWheel: handleWheel,
    },
  };
}
