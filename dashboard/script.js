const toast = document.querySelector('#toast');
let toastTimer;
function notify(message) {
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 3000);
}
function selectSection(name) {
  document.querySelectorAll('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.section === name));
  document.querySelector('#crumb').textContent = name.toUpperCase();
  if (name !== 'Home') notify(`${name} is coming soon to this dashboard preview.`);
}
document.querySelectorAll('[data-section]').forEach(button => button.addEventListener('click', () => selectSection(button.dataset.section)));
document.querySelector('.brand').addEventListener('click', () => selectSection('Home'));
document.querySelector('.profile-button').addEventListener('click', () => selectSection('Profile'));
document.querySelector('#all-worlds').addEventListener('click', () => selectSection('Worlds'));
document.querySelector('#view-all').addEventListener('click', () => selectSection('Worlds'));
document.querySelector('#play').addEventListener('click', () => notify('Echo Peaks is ready for your next adventure.'));
document.querySelectorAll('[data-world]').forEach(button => button.addEventListener('click', () => notify(`${button.dataset.world} selected.`)));
document.querySelector('.notification').addEventListener('click', () => notify('You’re all caught up!'));
document.querySelector('.hero-more').addEventListener('click', () => notify('Echo Peaks · Survival world'));
document.querySelector('#pause').addEventListener('click', event => {
  const paused = event.currentTarget.getAttribute('aria-label') === 'Pause update';
  event.currentTarget.setAttribute('aria-label', paused ? 'Resume update' : 'Pause update');
  event.currentTarget.textContent = paused ? '▶' : 'Ⅱ';
  document.querySelector('#download-status').textContent = paused ? 'Update paused' : 'Downloading assets...';
  document.querySelector('#download-speed').textContent = paused ? 'Paused' : '12.4 MB/s';
});
