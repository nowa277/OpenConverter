document.documentElement.dataset.os = /Mac/.test(navigator.userAgent)
  ? 'mac'
  : /Win/.test(navigator.userAgent)
    ? 'win'
    : 'linux';
