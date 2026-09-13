// Ayushflix Custom Video Player
let dashPlayer = null;
let hlsPlayer = null;
let controlsTimer = null;

const overlay = document.getElementById('playerOverlay');
const video = document.getElementById('videoPlayer');
const spinner = document.getElementById('playerSpinner');
const playPauseBtn = document.getElementById('btnPlayPause');
const iconPlay = document.getElementById('iconPlay');
const iconPause = document.getElementById('iconPause');
const timeline = document.getElementById('timeline');
const timeDisplay = document.getElementById('timeDisplay');
const btnRewind = document.getElementById('btnRewind');
const btnForward = document.getElementById('btnForward');
const btnMute = document.getElementById('btnMute');
const iconVolHigh = document.getElementById('iconVolHigh');
const iconVolMuted = document.getElementById('iconVolMuted');
const volSlider = document.getElementById('volSlider');
const btnFullscreen = document.getElementById('btnFullscreen');
const btnClose = document.getElementById('playerClose');

document.addEventListener('DOMContentLoaded', () => {
  initPlayerEvents();
});

function initPlayerEvents() {
  playPauseBtn.onclick = togglePlay;
  video.onclick = togglePlay;

  btnRewind.onclick = () => { video.currentTime = Math.max(0, video.currentTime - 10); };
  btnForward.onclick = () => { video.currentTime = Math.min(video.duration, video.currentTime + 10); };

  btnMute.onclick = () => {
    video.muted = !video.muted;
    updateVolumeIcon();
  };
  volSlider.oninput = (e) => {
    video.volume = parseFloat(e.target.value);
    video.muted = video.volume === 0;
    updateVolumeIcon();
  };

  timeline.oninput = (e) => {
    if (video.duration) {
      video.currentTime = (e.target.value / 100) * video.duration;
    }
  };

  btnFullscreen.onclick = toggleFullscreen;
  btnClose.onclick = closePlayer;

  // Video time & buffer update
  video.ontimeupdate = () => {
    if (video.duration) {
      timeline.value = (video.currentTime / video.duration) * 100;
      timeDisplay.textContent = `${formatTime(video.currentTime)} / ${formatTime(video.duration)}`;
    }
  };

  video.onwaiting = () => { spinner.style.display = 'block'; };
  video.onplaying = () => {
    spinner.style.display = 'none';
    iconPlay.style.display = 'none';
    iconPause.style.display = 'block';
  };
  video.onpause = () => {
    iconPlay.style.display = 'block';
    iconPause.style.display = 'none';
  };

  // Auto-hide controls on inactivity
  document.onmousemove = resetControlsTimeout;
  document.onkeydown = handleHotkeys;
}

function formatTime(secs) {
  if (isNaN(secs)) return "00:00";
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = Math.floor(secs % 60);
  const mm = m < 10 ? "0" + m : m;
  const ss = s < 10 ? "0" + s : s;
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

function togglePlay() {
  if (video.paused) {
    video.play();
  } else {
    video.pause();
  }
}

function updateVolumeIcon() {
  if (video.muted || video.volume === 0) {
    iconVolHigh.style.display = 'none';
    iconVolMuted.style.display = 'block';
  } else {
    iconVolHigh.style.display = 'block';
    iconVolMuted.style.display = 'none';
  }
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    overlay.requestFullscreen().catch(err => console.log(err));
  } else {
    document.exitFullscreen();
  }
}

function resetControlsTimeout() {
  overlay.classList.remove('idle');
  clearTimeout(controlsTimer);
  controlsTimer = setTimeout(() => {
    if (!video.paused) {
      overlay.classList.add('idle');
    }
  }, 3500);
}

function handleHotkeys(e) {
  if (!overlay.classList.contains('active')) {
    if (e.key === 'Escape') {
      document.getElementById('detailsModal').classList.remove('open');
    }
    return;
  }

  if (e.key === ' ' || e.key === 'k') {
    e.preventDefault();
    togglePlay();
  } else if (e.key === 'ArrowLeft') {
    video.currentTime = Math.max(0, video.currentTime - 10);
  } else if (e.key === 'ArrowRight') {
    video.currentTime = Math.min(video.duration, video.currentTime + 10);
  } else if (e.key === 'ArrowUp') {
    video.volume = Math.min(1, video.volume + 0.1);
    volSlider.value = video.volume;
    updateVolumeIcon();
  } else if (e.key === 'ArrowDown') {
    video.volume = Math.max(0, video.volume - 0.1);
    volSlider.value = video.volume;
    updateVolumeIcon();
  } else if (e.key === 'm') {
    btnMute.click();
  } else if (e.key === 'f') {
    toggleFullscreen();
  } else if (e.key === 'Escape') {
    closePlayer();
  }
  resetControlsTimeout();
}

async function launchPlayer(title, subjectId, se = 0, ep = 0) {
  document.getElementById('detailsModal').classList.remove('open');
  document.getElementById('playerTitle').textContent = title;
  overlay.classList.add('active');
  spinner.style.display = 'block';

  try {
    const res = await fetch(`/api/streams?id=${subjectId}&se=${se}&ep=${ep}`);
    const data = await res.json();
    const streams = data.streams || [];

    if (streams.length === 0) {
      alert("No streams available for this title.");
      closePlayer();
      return;
    }

    const stream = streams[0];
    playStream(stream.url, stream.type);
  } catch (err) {
    console.error("Stream launch error:", err);
    alert("Error loading stream.");
    closePlayer();
  }
}

function playStream(streamUrl, type) {
  // Destroy existing player instances
  if (dashPlayer) {
    dashPlayer.destroy();
    dashPlayer = null;
  }
  if (hlsPlayer) {
    hlsPlayer.destroy();
    hlsPlayer = null;
  }

  if (type === 'dash') {
    dashPlayer = dashjs.MediaPlayer().create();
    dashPlayer.initialize(video, streamUrl, true);
    dashPlayer.on(dashjs.MediaPlayer.events.CAN_PLAY, () => {
      video.play().catch(e => console.log("Autoplay error:", e));
    });
  } else if (type === 'hls' && Hls.isSupported()) {
    hlsPlayer = new Hls();
    hlsPlayer.loadSource(streamUrl);
    hlsPlayer.attachMedia(video);
    hlsPlayer.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(e => console.log("Autoplay error:", e));
    });
  } else {
    video.src = streamUrl;
    video.play().catch(e => console.log("Autoplay error:", e));
  }
}

function closePlayer() {
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(e => {});
  }
  video.pause();
  video.src = "";
  if (dashPlayer) {
    dashPlayer.destroy();
    dashPlayer = null;
  }
  if (hlsPlayer) {
    hlsPlayer.destroy();
    hlsPlayer = null;
  }
  overlay.classList.remove('active');
}
