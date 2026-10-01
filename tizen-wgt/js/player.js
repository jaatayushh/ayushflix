// Ayushflix TV Video Player (HLS.js with Multi-Audio & Remote Controls)
const Player = {
    hls: null,
    video: null,
    container: null,
    osdTimeout: null,
    currentMedia: null,
    pendingResumeTime: 0,

    init: function() {
        this.video = document.getElementById('video-player');
        this.container = document.getElementById('player-container');

        this.video.addEventListener('timeupdate', () => this.updateProgress());
        this.video.addEventListener('ended', () => this.onEnded());
        this.video.addEventListener('playing', () => {
            if (this.pendingResumeTime > 0) {
                this.video.currentTime = this.pendingResumeTime;
                this.pendingResumeTime = 0;
            }
        });
    },

    isOpen: function() {
        return this.container && this.container.style.display === 'block';
    },

    play: function(streamUrl, mediaInfo, startTime = 0) {
        this.currentMedia = mediaInfo;
        this.pendingResumeTime = startTime;
        this.container.style.display = 'block';

        if (this.hls) {
            this.hls.destroy();
            this.hls = null;
        }

        if (Hls.isSupported()) {
            this.hls = new Hls({
                enableWorker: true,
                lowLatencyMode: false,
                capLevelToPlayerSize: true,
                maxBufferLength: 30,
                maxMaxBufferLength: 60
            });
            this.hls.loadSource(streamUrl);
            this.hls.attachMedia(this.video);

            this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
                this.populateAudioTracks();
                this.video.play().catch(() => {});
            });

            this.hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => {
                this.populateAudioTracks();
            });

            this.hls.on(Hls.Events.ERROR, (event, data) => {
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            this.hls.startLoad();
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            this.hls.recoverMediaError();
                            break;
                        default:
                            this.hls.destroy();
                            break;
                    }
                }
            });
        } else if (this.video.canPlayType('application/vnd.apple.mpegurl')) {
            this.video.src = streamUrl;
            this.video.play().catch(() => {});
            this.populateAudioTracks();
        }

        this.showOsd();
        const playBtn = document.getElementById('btn-osd-play');
        if (playBtn) SpatialNav.focus(playBtn);
    },

    close: function() {
        if (this.hls) {
            this.hls.destroy();
            this.hls = null;
        }
        if (this.video) {
            this.video.pause();
            this.video.removeAttribute('src');
            this.video.load();
        }
        this.container.style.display = 'none';
        const heroBtn = document.getElementById('hero-play-btn');
        if (heroBtn) SpatialNav.focus(heroBtn);
    },

    togglePlay: function() {
        if (!this.video) return;
        if (this.video.paused) {
            this.video.play();
            document.getElementById('btn-osd-play').innerText = 'Pause';
        } else {
            this.video.pause();
            document.getElementById('btn-osd-play').innerText = 'Play';
        }
        this.showOsd();
    },

    seek: function(seconds) {
        if (!this.video) return;
        this.video.currentTime = Math.max(0, Math.min(this.video.duration || 0, this.video.currentTime + seconds));
        this.showOsd();
    },

    updateProgress: function() {
        const bar = document.getElementById('player-progress');
        if (bar && this.video && this.video.duration) {
            const pct = (this.video.currentTime / this.video.duration) * 100;
            bar.style.width = pct + '%';
        }
    },

    populateAudioTracks: function() {
        const menu = document.getElementById('audio-menu');
        menu.innerHTML = '';

        // 1. Check for backend provided language tracks (e.g. Castle TV Hindi, English, Tamil, etc.)
        if (this.currentMedia && this.currentMedia.tracks && this.currentMedia.tracks.length > 1) {
            document.getElementById('btn-osd-audio').style.display = 'inline-block';
            this.currentMedia.tracks.forEach(track => {
                const btn = document.createElement('button');
                btn.className = 'audio-track-item focusable ' + (track.selected ? 'active' : '');
                btn.innerText = track.name || `Language ${track.languageId}`;
                btn.onclick = () => {
                    const currentTime = this.video.currentTime;
                    menu.style.display = 'none';
                    window.App.playMedia(this.currentMedia, this.currentMedia.episodeId, track.languageId);
                    this.pendingResumeTime = currentTime;
                };
                menu.appendChild(btn);
            });
            return;
        }

        // 2. Fallback to HLS embedded audio tracks
        if (!this.hls || !this.hls.audioTracks || this.hls.audioTracks.length <= 1) {
            document.getElementById('btn-osd-audio').style.display = 'none';
            return;
        }

        document.getElementById('btn-osd-audio').style.display = 'inline-block';
        this.hls.audioTracks.forEach((track, i) => {
            const btn = document.createElement('button');
            btn.className = 'audio-track-item focusable ' + (i === this.hls.audioTrack ? 'active' : '');
            btn.innerText = track.name || track.lang || `Track ${i + 1}`;
            btn.onclick = () => {
                this.hls.audioTrack = i;
                document.querySelectorAll('.audio-track-item').forEach((b, idx) => {
                    b.classList.toggle('active', idx === i);
                });
                menu.style.display = 'none';
                SpatialNav.focus(document.getElementById('btn-osd-audio'));
            };
            menu.appendChild(btn);
        });
    },

    toggleAudioModal: function() {
        const menu = document.getElementById('audio-menu');
        if (!menu) return;
        if (menu.style.display === 'flex') {
            menu.style.display = 'none';
            SpatialNav.focus(document.getElementById('btn-osd-audio'));
        } else {
            menu.style.display = 'flex';
            const first = menu.querySelector('.focusable');
            if (first) SpatialNav.focus(first);
        }
    },

    showOsd: function() {
        const osd = document.getElementById('player-osd');
        if (!osd) return;
        osd.classList.add('visible');
        clearTimeout(this.osdTimeout);
        this.osdTimeout = setTimeout(() => {
            const menu = document.getElementById('audio-menu');
            if (!menu || menu.style.display !== 'flex') {
                osd.classList.remove('visible');
            }
        }, 4000);
    },

    handleKey: function(keyCode, e) {
        this.showOsd();
        switch (keyCode) {
            case 37: // Left -> Rewind 10s
                this.seek(-10);
                e.preventDefault();
                break;
            case 39: // Right -> Fast Forward 10s
                this.seek(10);
                e.preventDefault();
                break;
            case 38: // Up
            case 40: // Down
                SpatialNav.navigate(keyCode === 38 ? 'up' : 'down', false);
                e.preventDefault();
                break;
            case 13: // Enter
                if (SpatialNav.currentFocus) SpatialNav.currentFocus.click();
                e.preventDefault();
                break;
            case 415: // MediaPlay
            case 19:  // MediaPause
            case 10252: // MediaPlayPause
                this.togglePlay();
                e.preventDefault();
                break;
            case 10009: // Return / Back
            case 27:
            case 8:
                const menu = document.getElementById('audio-menu');
                if (menu && menu.style.display === 'flex') {
                    menu.style.display = 'none';
                    SpatialNav.focus(document.getElementById('btn-osd-audio'));
                } else {
                    this.close();
                }
                e.preventDefault();
                break;
        }
    },

    onEnded: function() {
        this.close();
    }
};

window.Player = Player;
