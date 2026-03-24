const DEFAULT_MAX_TILT = 12;
const DEFAULT_SCALE = 1.02;

export interface CardPointerPosition {
  clientX: number;
  clientY: number;
}

export interface CardRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface CardTiltState {
  rotateX: number;
  rotateY: number;
  glareX: number;
  glareY: number;
  scale: number;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function round(value: number) {
  return Math.round(value * 100) / 100;
}

export function calculateCardTilt(
  pointer: CardPointerPosition,
  rect: CardRect,
  maxTilt = DEFAULT_MAX_TILT,
): CardTiltState {
  const relativeX = clamp((pointer.clientX - rect.left) / rect.width, 0, 1);
  const relativeY = clamp((pointer.clientY - rect.top) / rect.height, 0, 1);

  return {
    rotateX: round((0.5 - relativeY) * maxTilt * 2),
    rotateY: round((relativeX - 0.5) * maxTilt * 2),
    glareX: round(relativeX * 100),
    glareY: round(relativeY * 100),
    scale: DEFAULT_SCALE,
  };
}

export function getRestingCardTilt(): CardTiltState {
  return {
    rotateX: 0,
    rotateY: 0,
    glareX: 50,
    glareY: 50,
    scale: 1,
  };
}
