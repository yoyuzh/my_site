import { useState, useEffect, useRef, useCallback } from 'react';
import type { MouseEvent as ReactMouseEvent } from 'react';
import { FileItem } from '../api/types';
import { getWorkspaceItemLogicalPath } from '../lib/workspace-folder-tree';

export interface DragState {
  isDragging: boolean;
  sourceItems: FileItem[];
  currentMousePos: { x: number; y: number };
  velocity: { x: number; y: number };
  tiltAngle: number;
}

export interface DropTarget {
  path: string;
  type: 'folder-item' | 'tree-node';
  element: HTMLElement;
}

export function useWorkspaceDragMove(
  onDrop: (sourceItems: FileItem[], targetPath: string) => void
) {
  const [dragState, setDragState] = useState<DragState>({
    isDragging: false,
    sourceItems: [],
    currentMousePos: { x: 0, y: 0 },
    velocity: { x: 0, y: 0 },
    tiltAngle: 0,
  });

  const dragStartTimer = useRef<number | null>(null);
  const pendingMouseUpHandler = useRef<((event: MouseEvent) => void) | null>(null);
  const lastMousePos = useRef({ x: 0, y: 0 });
  const lastTime = useRef(Date.now());
  const dropTargets = useRef<Map<HTMLElement, string>>(new Map());
  const [activeDropTarget, setActiveDropTarget] = useState<string | null>(null);
  const hoverTimer = useRef<number | null>(null);

  const clearPendingDragStart = useCallback(() => {
    if (dragStartTimer.current) {
      window.clearTimeout(dragStartTimer.current);
      dragStartTimer.current = null;
    }
    if (pendingMouseUpHandler.current) {
      window.removeEventListener('mouseup', pendingMouseUpHandler.current);
      pendingMouseUpHandler.current = null;
    }
    document.body.style.removeProperty('user-select');
  }, []);

  const updateVelocity = useCallback((x: number, y: number) => {
    const now = Date.now();
    const dt = now - lastTime.current;
    if (dt <= 0) return;

    const dx = x - lastMousePos.current.x;
    const dy = y - lastMousePos.current.y;

    const vx = dx / dt;
    const vy = dy / dt;

    setDragState((prev) => {
      // Calculate tilt based on horizontal velocity
      // vx is in pixels/ms. Max tilt ~10deg.
      // Suppose vx = 2px/ms is "fast" -> 10deg.
      let targetTilt = vx * 5; 
      if (targetTilt > 10) targetTilt = 10;
      if (targetTilt < -10) targetTilt = -10;

      // Smooth return when slowing down
      const tilt = prev.tiltAngle + (targetTilt - prev.tiltAngle) * 0.2;

      return {
        ...prev,
        currentMousePos: { x, y },
        velocity: { x: vx, y: vy },
        tiltAngle: tilt,
      };
    });

    lastMousePos.current = { x, y };
    lastTime.current = now;
  }, []);

  const onMouseDown = useCallback((items: FileItem[], event: ReactMouseEvent) => {
    if (event.button !== 0) return; // Only left click

    clearPendingDragStart();

    const startX = event.clientX;
    const startY = event.clientY;
    const handlePendingMouseUp = () => {
      clearPendingDragStart();
    };
    pendingMouseUpHandler.current = handlePendingMouseUp;
    window.addEventListener('mouseup', handlePendingMouseUp, { once: true });
    document.body.style.setProperty('user-select', 'none');

    dragStartTimer.current = window.setTimeout(() => {
      pendingMouseUpHandler.current = null;
      setDragState({
        isDragging: true,
        sourceItems: items,
        currentMousePos: { x: startX, y: startY },
        velocity: { x: 0, y: 0 },
        tiltAngle: 0,
      });
      lastMousePos.current = { x: startX, y: startY };
      lastTime.current = Date.now();
      dragStartTimer.current = null;
    }, 140); // 120ms - 160ms delay
  }, [clearPendingDragStart]);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (dragState.isDragging) {
        updateVelocity(e.clientX, e.clientY);
        
        // Find drop target under mouse
        let foundTarget: string | null = null;
        const elementsUnderMouse = document.elementsFromPoint(e.clientX, e.clientY);
        
        for (const el of elementsUnderMouse) {
          if (dropTargets.current.has(el as HTMLElement)) {
            foundTarget = dropTargets.current.get(el as HTMLElement)!;
            break;
          }
        }

        if (foundTarget !== activeDropTarget) {
          setActiveDropTarget(foundTarget);
          if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
          
          if (foundTarget) {
            hoverTimer.current = window.setTimeout(() => {
              // Trigger auto-expand if it's a tree node
              const event = new CustomEvent('workspace-tree-auto-expand', { detail: { path: foundTarget } });
              window.dispatchEvent(event);
            }, 450); // 400ms - 500ms
          }
        }
      }
    };

    const handleMouseUp = (e: MouseEvent) => {
      clearPendingDragStart();

      if (dragState.isDragging) {
        if (activeDropTarget) {
          // Check if dropping onto self or parent (invalid targets)
          const isInvalid = dragState.sourceItems.some(item => {
             const itemPath = getWorkspaceItemLogicalPath(item);
             return activeDropTarget === itemPath || activeDropTarget.startsWith(itemPath + '/');
          });

          if (!isInvalid) {
            onDrop(dragState.sourceItems, activeDropTarget);
          }
        }

        setDragState({
          isDragging: false,
          sourceItems: [],
          currentMousePos: { x: 0, y: 0 },
          velocity: { x: 0, y: 0 },
          tiltAngle: 0,
        });
        setActiveDropTarget(null);
      }
    };

    if (dragState.isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    } else {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [activeDropTarget, clearPendingDragStart, dragState.isDragging, onDrop, updateVelocity]);

  const registerDropTarget = useCallback((el: HTMLElement | null, path: string) => {
    if (el) {
      dropTargets.current.set(el, path);
      return;
    }
    for (const [element, elementPath] of dropTargets.current.entries()) {
      if (elementPath === path && !document.body.contains(element)) {
        dropTargets.current.delete(element);
      }
    }
  }, []);

  useEffect(() => clearPendingDragStart, [clearPendingDragStart]);

  return {
    dragState,
    onMouseDown,
    registerDropTarget,
    activeDropTarget,
  };
}
