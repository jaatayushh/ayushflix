// Ayushflix Smart TV Main Application Controller
const App = {
    API_URL: window.AYUSHFLIX_API_URL || 'http://localhost:8080',
    isBackendRelay: false,
    currentHero: null,

    init: async function() {
        SpatialNav.init();
        Player.init();
        this.bindEvents();
        await this.checkAutoUpdate();
        await this.loadHome();
    },

    bindEvents: function() {
        document.getElementById('nav-home').onclick = () => this.showHome();
        document.getElementById('nav-search').onclick = () => this.showSearch();
        document.getElementById('search-input').oninput = (e) => this.performSearch(e.target.value);
        document.getElementById('hero-play-btn').onclick = () => {
            if (this.currentHero) this.playMedia(this.currentHero);
        };
        document.getElementById('hero-more-btn').onclick = () => {
            if (this.currentHero) this.openDetails(this.currentHero);
        };
        document.getElementById('modal-close-btn').onclick = () => this.closeModal();
    },

    // -------------------------------------------------------------
    // DYNAMIC GITHUB AUTO-UPDATE
    // -------------------------------------------------------------
    checkAutoUpdate: async function() {
        try {
            const updateUrl = 'https://raw.githubusercontent.com/jaatayushh/ayushflix/main/tizen-wgt/js/scrapers/ayushflix-core.js';
            const res = await fetch(updateUrl, { cache: 'no-cache' });
            if (res.ok) {
                const scriptText = await res.text();
                const scriptEl = document.createElement('script');
                scriptEl.text = scriptText;
                document.head.appendChild(scriptEl);
                console.log('Synced latest scrapers from GitHub');
            }
        } catch (_) {
            console.log('Using bundled scrapers');
        }
    },

    // -------------------------------------------------------------
    // LOAD HOME CATALOG
    // -------------------------------------------------------------
    loadHome: async function() {
        const rowsContainer = document.getElementById('rows-container');
        rowsContainer.innerHTML = '<div style="padding: 24px; font-size: 20px; color: #aaa;">Loading Ayushflix Catalog...</div>';

        // 1. Try local dev server API if available
        try {
            const apiRes = await fetch('/api/home');
            if (apiRes.ok) {
                const apiData = await apiRes.json();
                if (apiData.success && apiData.rows && apiData.rows.length > 0) {
                    this.isBackendRelay = true;
                    rowsContainer.innerHTML = '';
                    if (apiData.hero) {
                        this.currentHero = apiData.hero;
                        document.getElementById('hero-title').innerText = apiData.hero.title;
                        document.getElementById('hero-desc').innerText = apiData.hero.overview || `Watch ${apiData.hero.title} in high definition with multi-language audio on Ayushflix.`;
                        document.getElementById('hero-banner').style.backgroundImage = `url('${apiData.hero.backdrop || apiData.hero.poster}')`;
                    }
                    apiData.rows.forEach(r => {
                        this.renderRow(r.title, r.items, r.provider);
                    });
                    SpatialNav.focus(document.getElementById('hero-play-btn'));
                    return;
                }
            }
        } catch (_) {}

        // 2. Direct fetch fallback for Standalone Tizen TV (without backend server)
        try {
            const nfUrl = 'https://net52.cc/mobile/search.php?s=';
            const nfRes = await fetch(nfUrl);
            const nfData = await nfRes.json();

            const pvUrl = 'https://net52.cc/mobile/pv/search.php?s=a';
            const pvRes = await fetch(pvUrl);
            const pvData = await pvRes.json();

            rowsContainer.innerHTML = '';

            if (nfData.searchResult && nfData.searchResult.length) {
                const first = nfData.searchResult[0];
                this.currentHero = {
                    id: `nf_${first.id}`,
                    sourceId: first.id,
                    provider: 'netflix',
                    title: first.t,
                    year: first.y || '',
                    poster: `https://imgcdn.kim/poster/v/${first.id}.jpg`
                };
                document.getElementById('hero-title').innerText = first.t;
                document.getElementById('hero-desc').innerText = `Watch ${first.t} in high definition with multi-language audio on Ayushflix.`;
                document.getElementById('hero-banner').style.backgroundImage = `url('https://imgcdn.kim/poster/v/${first.id}.jpg')`;

                this.renderRow('Trending on Netflix', nfData.searchResult.map(i => ({
                    id: `nf_${i.id}`,
                    sourceId: i.id,
                    title: i.t,
                    year: i.y || '',
                    poster: `https://imgcdn.kim/poster/v/${i.id}.jpg`,
                    provider: 'netflix'
                })), 'netflix');
            }

            if (pvData.searchResult && pvData.searchResult.length) {
                this.renderRow('Amazon Prime Video Hits', pvData.searchResult.slice(0, 20).map(i => ({
                    id: `pv_${i.id}`,
                    sourceId: i.id,
                    title: i.t,
                    year: i.y || '',
                    poster: `https://imgcdn.kim/poster/v/${i.id}.jpg`,
                    provider: 'prime'
                })), 'prime');
            }

            SpatialNav.focus(document.getElementById('hero-play-btn'));
        } catch (e) {
            rowsContainer.innerHTML = `<div style="padding: 24px; color: #E50914;">Failed to load catalog: ${e.message}</div>`;
        }
    },

    renderRow: function(title, items, provider) {
        if (!items || items.length === 0) return;
        const rowsContainer = document.getElementById('rows-container');
        const row = document.createElement('div');
        row.className = 'media-row';

        const rowTitle = document.createElement('div');
        rowTitle.className = 'row-title';
        rowTitle.innerText = title;
        row.appendChild(rowTitle);

        const shelf = document.createElement('div');
        shelf.className = 'row-shelf';

        items.forEach(item => {
            const card = document.createElement('div');
            card.className = 'card focusable';
            const posterUrl = item.poster || (item.sourceId ? `https://imgcdn.kim/poster/v/${item.sourceId}.jpg` : '');
            const itemTitle = item.title || item.t || 'Watch Now';

            card.innerHTML = `
                <img src="${posterUrl}" loading="lazy" alt="${itemTitle}" onerror="this.src='https://placehold.co/180x270/1a1a1a/ffffff?text=Ayushflix'"/>
                <div class="card-title">${itemTitle}</div>
            `;
            card.onclick = () => {
                this.openDetails(item);
            };
            shelf.appendChild(card);
        });

        row.appendChild(shelf);
        rowsContainer.appendChild(row);
    },

    // -------------------------------------------------------------
    // DETAILS & EPISODES MODAL
    // -------------------------------------------------------------
    openDetails: async function(media) {
        const modal = document.getElementById('details-modal');
        const posterUrl = media.poster || (media.sourceId ? `https://imgcdn.kim/poster/v/${media.sourceId}.jpg` : '');
        const title = media.title || media.t || '';

        document.getElementById('modal-title').innerText = title;
        document.getElementById('modal-meta').innerText = `${media.year || (media.type === 'series' ? 'Web Series' : 'Movie')} • ${(media.provider || 'Ayushflix').toUpperCase()}`;
        document.getElementById('modal-header').style.backgroundImage = `url('${media.backdrop || posterUrl}')`;
        document.getElementById('modal-desc').innerText = media.overview || `Stream ${title} with high quality multi-language audio and seamless playback.`;

        const episodesShelf = document.getElementById('episodes-shelf');
        episodesShelf.innerHTML = '<div style="color: #aaa;">Loading episodes and tracks...</div>';
        modal.style.display = 'flex';

        try {
            if (this.isBackendRelay) {
                const res = await fetch(`/api/details?id=${encodeURIComponent(media.id)}`);
                const data = await res.json();

                if (data.success && data.episodes && data.episodes.length > 0) {
                    episodesShelf.innerHTML = '';
                    data.episodes.forEach(ep => {
                        const epCard = document.createElement('div');
                        epCard.className = 'episode-card focusable';
                        const epName = ep.title || `Episode ${ep.episodeNumber}`;
                        const trackNames = ep.tracks ? ep.tracks.map(t => t.name).join(', ') : '';
                        epCard.innerHTML = `
                            <strong>${epName}</strong>
                            <span style="font-size: 12px; color: #00e676; margin-top: 4px;">▶ Play Now</span>
                            ${trackNames ? `<span style="font-size: 11px; color: #888; margin-top: 2px;">${trackNames}</span>` : ''}
                        `;
                        epCard.onclick = () => {
                            this.closeModal();
                            this.playMedia(media, ep.id);
                        };
                        episodesShelf.appendChild(epCard);
                    });
                } else {
                    episodesShelf.innerHTML = '<div style="color: #aaa;">Full Movie • Ready to Stream</div>';
                }
            } else {
                // Standalone fallback
                episodesShelf.innerHTML = '<div style="color: #aaa;">Full Feature Title</div>';
            }
        } catch (_) {
            episodesShelf.innerHTML = '<div style="color: #aaa;">Standard Playback</div>';
        }

        const playBtn = document.getElementById('modal-play-btn');
        playBtn.onclick = () => {
            this.closeModal();
            this.playMedia(media);
        };
        SpatialNav.focus(playBtn);
    },

    closeModal: function() {
        const modal = document.getElementById('details-modal');
        if (modal) modal.style.display = 'none';
        SpatialNav.focus(document.getElementById('hero-play-btn'));
    },

    // -------------------------------------------------------------
    // PLAYBACK
    // -------------------------------------------------------------
    playMedia: async function(media, episodeId, languageId) {
        try {
            if (this.isBackendRelay) {
                let streamUrl = `/api/stream?id=${encodeURIComponent(media.id)}`;
                if (episodeId) streamUrl += `&episodeId=${encodeURIComponent(episodeId)}`;
                if (languageId) streamUrl += `&languageId=${encodeURIComponent(languageId)}`;

                const streamRes = await fetch(streamUrl);
                const streamData = await streamRes.json();
                if (streamData.success && streamData.streamUrl) {
                    Player.play(streamData.streamUrl, {
                        ...media,
                        episodeId: episodeId,
                        title: media.title || media.t || streamData.title,
                        tracks: streamData.tracks || []
                    });
                    return;
                }
            }

            // Standalone Tizen Fallback
            const sourceId = media.sourceId || media.id.replace(/^(nf|pv|ct)_/, '');
            const isPv = media.provider === 'prime' || media.id.startsWith('pv_');
            const playlistUrl = isPv
                ? `https://net52.cc/mobile/pv/playlist.php?id=${sourceId}`
                : `https://net52.cc/mobile/playlist.php?id=${sourceId}`;

            const res = await fetch(playlistUrl);
            const data = await res.json();

            if (Array.isArray(data) && data[0]?.sources?.length) {
                const rawFile = data[0].sources[0].file;
                const m3u8Url = rawFile.startsWith('http') ? rawFile : `https://net52.cc${rawFile}`;
                Player.play(m3u8Url, media);
            } else {
                alert('No playable stream found for this title.');
            }
        } catch (e) {
            alert('Failed to resolve stream link: ' + e.message);
        }
    },

    // -------------------------------------------------------------
    // SEARCH
    // -------------------------------------------------------------
    showSearch: function() {
        document.getElementById('main-container').style.display = 'none';
        document.getElementById('search-container').style.display = 'block';
        document.getElementById('nav-home').classList.remove('active');
        document.getElementById('nav-search').classList.add('active');
        const input = document.getElementById('search-input');
        SpatialNav.focus(input);
    },

    showHome: function() {
        document.getElementById('search-container').style.display = 'none';
        document.getElementById('main-container').style.display = 'block';
        document.getElementById('nav-search').classList.remove('active');
        document.getElementById('nav-home').classList.add('active');
        SpatialNav.focus(document.getElementById('hero-play-btn'));
    },

    isSearchOpen: function() {
        const c = document.getElementById('search-container');
        return c && c.style.display === 'block';
    },

    performSearch: async function(query) {
        if (!query || query.trim().length < 2) return;
        const grid = document.getElementById('search-grid');
        grid.innerHTML = '<div style="font-size:18px; color:#aaa;">Searching Ayushflix...</div>';

        try {
            let results = [];
            if (this.isBackendRelay) {
                const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
                const data = await res.json();
                results = data.results || [];
            } else {
                const res = await fetch(`https://net52.cc/mobile/search.php?s=${encodeURIComponent(query)}`);
                const data = await res.json();
                results = (data.searchResult || []).map(i => ({
                    id: `nf_${i.id}`,
                    sourceId: i.id,
                    provider: 'Netflix',
                    title: i.t,
                    year: i.y || '',
                    poster: `https://imgcdn.kim/poster/v/${i.id}.jpg`
                }));
            }

            grid.innerHTML = '';
            if (results && results.length) {
                results.forEach(item => {
                    const card = document.createElement('div');
                    card.className = 'card focusable';
                    const posterUrl = item.poster || `https://imgcdn.kim/poster/v/${item.sourceId}.jpg`;
                    card.innerHTML = `
                        <img src="${posterUrl}" loading="lazy" alt="${item.title}" onerror="this.src='https://placehold.co/180x270/1a1a1a/ffffff?text=Ayushflix'"/>
                        <div class="card-title">${item.title}</div>
                    `;
                    card.onclick = () => {
                        this.openDetails(item);
                    };
                    grid.appendChild(card);
                });
            } else {
                grid.innerHTML = '<div style="color:#aaa;">No matches found. Try another movie or series name.</div>';
            }
        } catch (e) {
            grid.innerHTML = `<div>Search error: ${e.message}</div>`;
        }
    }
};

window.App = App;
window.onload = () => App.init();
