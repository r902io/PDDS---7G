import React, { useEffect } from 'react';
import { IconoCerrar } from '../iconos';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  titulo: string;
  subtitulo?: string;
  children: React.ReactNode;
  tamano?: 'normal' | 'ancho' | 'completo';
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  titulo,
  subtitulo,
  children,
  tamano = 'normal',
}) => {
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  let widthClass = 'max-w-xl';
  if (tamano === 'ancho') widthClass = 'max-w-3xl';
  if (tamano === 'completo') widthClass = 'max-w-5xl';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-bg/80"
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
      onClick={onClose}
    >
      <div
        className={`w-full ${widthClass} bg-panel2 border border-borde rounded-lg shadow-2xl flex flex-col max-h-[90vh] overflow-hidden`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-borde p-4">
          <div>
            <h2 className="text-base font-semibold text-texto uppercase tracking-wide">{titulo}</h2>
            {subtitulo && <p className="text-xs text-texto2 mt-0.5">{subtitulo}</p>}
          </div>
          <button
            onClick={onClose}
            className="text-texto2 hover:text-texto p-1 rounded hover:bg-panel transition-colors"
            aria-label="Cerrar"
          >
            <IconoCerrar tamano={20} />
          </button>
        </div>
        <div className="p-4 overflow-y-auto space-y-4">{children}</div>
      </div>
    </div>
  );
};
