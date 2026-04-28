import { useState, useEffect } from 'react';

let isOpen = false;
let listeners: ((open: boolean) => void)[] = [];

export const useUploadPanelStore = () => {
  const [open, setOpen] = useState(isOpen);

  useEffect(() => {
    const l = (v: boolean) => setOpen(v);
    listeners.push(l);
    return () => {
      listeners = listeners.filter((i) => i !== l);
    };
  }, []);

  const toggle = () => {
    isOpen = !isOpen;
    listeners.forEach((l) => l(isOpen));
  };

  const set = (v: boolean) => {
    isOpen = v;
    listeners.forEach((l) => l(isOpen));
  };

  return { open, toggle, set };
};
