/**
 * Preload — exposes a single safe API to the renderer.
 * All traffic goes through IPC channel "process-message" (no `ipcRenderer`
 * is leaked to the window).
 */
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  invoke: (method, data) => ipcRenderer.invoke('process-message', { method, data }),
  on: (channel, listener) => {
    const allowed = ['convert:progress', 'win:maximizedChanged'];
    if (!allowed.includes(channel)) return;
    const wrapped = (_evt, payload) => listener(payload);
    ipcRenderer.on(channel, wrapped);
    return () => ipcRenderer.removeListener(channel, wrapped);
  },
});
