import { Coordinate, Order, RoadBlock } from '../types/logistics';

export interface ParsedOrderRecord {
  day: number;
  hour: number;
  minute: number;
  coord: Coordinate;
  clientCode: string;
  quantity: number;
  deadlineHours: 4 | 8 | 12 | 18 | 36;
  rawLine: string;
}

export interface ParsedRoadBlockRecord {
  startDay: number;
  startHour: number;
  startMinute: number;
  endDay: number;
  endHour: number;
  endMinute: number;
  from: Coordinate;
  to: Coordinate;
  rawLine: string;
}

/**
 * Parser for ventasaaaamm
 * Expected format: ##d##h##m:posX,posY,cIdCliente,qq,hl
 * Example: 11d13h31m:45,43,c9167,12,36
 */
export function parseSalesFile(content: string, monthYear: string = '202609'): {
  orders: ParsedOrderRecord[];
  errors: string[];
} {
  const lines = content.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0 && !l.startsWith('#'));
  const orders: ParsedOrderRecord[] = [];
  const errors: string[] = [];

  const regex = /^(\d{1,2})d(\d{1,2})h(\d{1,2})m:(\d+),(\d+),([a-zA-Z0-9_-]+),(\d+),(\d+)$/;

  lines.forEach((line, index) => {
    const match = line.match(regex);
    if (!match) {
      errors.push(`Línea ${index + 1}: Formato inválido. Debe ser ##d##h##m:posX,posY,cIdCliente,qq,hl (encontrado: "${line}")`);
      return;
    }

    const day = parseInt(match[1], 10);
    const hour = parseInt(match[2], 10);
    const minute = parseInt(match[3], 10);
    const posX = parseInt(match[4], 10);
    const posY = parseInt(match[5], 10);
    const clientCode = match[6];
    const quantity = parseInt(match[7], 10);
    const deadlineHours = parseInt(match[8], 10) as 4 | 8 | 12 | 18 | 36;

    if (posX < 0 || posX > 70 || posY < 0 || posY > 50) {
      errors.push(`Línea ${index + 1}: Coordenadas fuera de la retícula 70x50 km: (${posX}, ${posY})`);
      return;
    }

    if (day < 1 || day > 31 || hour > 23 || minute > 59) {
      errors.push(`Línea ${index + 1}: Fecha/hora fuera de rango (día 1-31, hora 0-23, min 0-59)`);
      return;
    }

    if (![4, 8, 12, 18, 36].includes(deadlineHours)) {
      errors.push(`Línea ${index + 1}: Plazo no permitido "${deadlineHours}h". Solo se admiten 4, 8, 12, 18 o 36 horas.`);
      return;
    }

    orders.push({
      day,
      hour,
      minute,
      coord: { x: posX, y: posY },
      clientCode,
      quantity,
      deadlineHours,
      rawLine: line,
    });
  });

  return { orders, errors };
}

/**
 * Parser for aaaamm.bloqueadas
 * Expected format: ##d##h##m-##d##h##m:x1,y1,x2,y2
 * Example: 01d06h00m-01d15h00m:31,21,34,21
 */
export function parseRoadBlocksFile(content: string, monthYear: string = '202609'): {
  blocks: ParsedRoadBlockRecord[];
  errors: string[];
} {
  const lines = content.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0 && !l.startsWith('#'));
  const blocks: ParsedRoadBlockRecord[] = [];
  const errors: string[] = [];

  const regex = /^(\d{1,2})d(\d{1,2})h(\d{1,2})m-(\d{1,2})d(\d{1,2})h(\d{1,2})m:(\d+),(\d+),(\d+),(\d+)$/;

  lines.forEach((line, index) => {
    const match = line.match(regex);
    if (!match) {
      errors.push(`Línea ${index + 1}: Formato inválido. Debe ser ##d##h##m-##d##h##m:x1,y1,x2,y2 (encontrado: "${line}")`);
      return;
    }

    const startDay = parseInt(match[1], 10);
    const startHour = parseInt(match[2], 10);
    const startMinute = parseInt(match[3], 10);
    const endDay = parseInt(match[4], 10);
    const endHour = parseInt(match[5], 10);
    const endMinute = parseInt(match[6], 10);
    const x1 = parseInt(match[7], 10);
    const y1 = parseInt(match[8], 10);
    const x2 = parseInt(match[9], 10);
    const y2 = parseInt(match[10], 10);

    if (x1 < 0 || x1 > 70 || y1 < 0 || y1 > 50 || x2 < 0 || x2 > 70 || y2 < 0 || y2 > 50) {
      errors.push(`Línea ${index + 1}: Coordenadas del tramo fuera de la retícula 70x50 km`);
      return;
    }

    if (startDay < 1 || startDay > 31 || endDay < 1 || endDay > 31
      || startHour > 23 || endHour > 23 || startMinute > 59 || endMinute > 59) {
      errors.push(`Línea ${index + 1}: Fecha/hora fuera de rango (día 1-31, hora 0-23, min 0-59)`);
      return;
    }

    blocks.push({
      startDay,
      startHour,
      startMinute,
      endDay,
      endHour,
      endMinute,
      from: { x: x1, y: y1 },
      to: { x: x2, y: y2 },
      rawLine: line,
    });
  });

  return { blocks, errors };
}
