export function getLocalStorageItem(key: string) {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function setLocalStorageItem(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Browser privacy policies can block localStorage. Keep the app usable.
  }
}

export function removeLocalStorageItem(key: string) {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Browser privacy policies can block localStorage. Keep the app usable.
  }
}

export function getSessionStorageItem(key: string) {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

export function setSessionStorageItem(key: string, value: string) {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    // Browser privacy policies can block sessionStorage. Keep the app usable.
  }
}
