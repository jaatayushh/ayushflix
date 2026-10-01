// Ayushflix Smart TV Main Application Controller
const App = {
    // Configurable API URL: Defaults to direct Tizen CORS client or backend relay
    API_URL: window.AYUSHFLIX_API_URL || 'https://net52.cc',
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
                // Inject updated scraper logic
                const scriptEl = document.createElement('script');
                scriptEl.text = scriptText;
                document.head.appendChild(scriptEl);
                console.log('Successfully synced latest scrapers from GitHub!');
            }
        } catch (_) {
            console.log('Using bundled scrapers (offline or GitHub unreachable)');
        }
    },

    // -------------------------------------------------------------
    // LOAD HOME CATALOG
    // -------------------------------------------------------------
    loadHome: async function() {
        const rowsContainer = document.getElementById('rows-container');
        rowsContainer.innerHTML = '<div style="padding: 20px; font-size: 20px;">Loading catalog...</div>';

        try {
            // Direct fetch from Netmirror (Unrestricted CORS in Tizen TV)
            const nfUrl = 'https://net52.cc/mobile/search.php?s=';
            const nfRes = await fetch(nfUrl);
            const nfData = await nfRes.json();

            // Prime Video row
            const pvUrl = 'https://net52.cc/mobile/pv/search.php?s=a';
            const pvRes = await fetch(pvUrl);
            const pvData = await pvRes.json();

            rowsContainer.innerHTML = '';

            // Setup Hero Banner from top item
            if (nfData.searchResult && nfData.searchResult.length) {
                const first = nfData.searchResult[0];
                this.currentHero = {
                    id: first.id,
                    provider: 'netflix',
                    title: first.t,
                    year: first.y || '',
                    poster: `https://imgcdn.kim/poster/v/${first.id}.jpg`
                };
                document.getElementById('hero-title').innerText = first.t;
                document.getElementById('hero-desc').innerText = `Watch ${first.t} in high definition with multi-language audio on Ayushflix.`;
                document.getElementById('hero-banner').style.backgroundImage = `url('https://imgcdn.kim/poster/v/${first.id}.jpg')`;

                // Add Netflix row
                this.renderRow('Trending on Netflix', nfData.searchResult, 'netflix');
            }

            if (pvData.searchResult && pvData.searchResult.length) {
                this.renderRow('Amazon Prime Video Hits', pvData.searchResult.slice(0, 20), 'prime');
            }

            // Set focus to the Play button
            SpatialNav.focus(document.getElementById('hero-play-btn'));
        } catch (e) {
            rowsContainer.innerHTML = `<div style="padding: 20px; color: #E50914;">Failed to load catalog: ${e.message}</div>`;
        }
    },

    renderRow: function(title, items, provider) {
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
            card.innerHTML = `
                <img src="https://imgcdn.kim/poster/v/${item.id}.jpg" loading="lazy" alt="${item.t}"/>
                <div class="card-title">${item.t}</div>
            `;
            card.onclick = () => {
                this.openDetails({
                    id: item.id,
                    provider: provider,
                    title: item.t,
                    year: item.y || '',
                    poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`
                });
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
        document.getElementById('modal-title').innerText = media.title;
        document.getElementById('modal-meta').innerText = `${media.year || ''} • ${media.provider.toUpperCase()}`;
        document.getElementById('modal-header').style.backgroundImage = `url('${media.poster}')`;
        document.getElementById('modal-desc').innerText = `Stream ${media.title} with multi-language audio and seamless playback.`;

        const episodesShelf = document.getElementById('episodes-shelf');
        episodesShelf.innerHTML = '<div>Checking for episodes...</div>';
        modal.style.display = 'flex';

        // Check if TV series
        try {
            const epUrl = media.provider === 'prime'
                ? `https://net52.cc/mobile/pv/episodes.php?s=${media.id}`
                : `https://net52.cc/mobile/episodes.php?s=${media.id}`;
            const res = await fetch(epUrl);
            const data = await res.json();

            if (data.nextPageSeason) {
                // TV Series
                episodesShelf.innerHTML = '';
                const eps = [
                    { number: 1, title: 'Episode 1' },
                    { number: 2, title: 'Episode 2' },
                    { number: 3, title: 'Episode 3' },
                    { number: 4, title: 'Episode 4' }
                ];
                eps.forEach(ep => {
                    const epCard = document.createElement('div');
                    epCard.className = 'episode-card focusable';
                    epCard.innerHTML = `<strong>${ep.title}</strong><span style="font-size:12px; color:#aaa;">Play Now</span>`;
                    epCard.onclick = () => {
                        this.closeModal();
                        this.playMedia(media);
                    };
                    episodesShelf.appendChild(epCard);
                });
            } else {
                episodesShelf.innerHTML = '<div style="color:#aaa;">Full Feature Movie</div>';
            }
        } catch (_) {
            episodesShelf.innerHTML = '<div style="color:#aaa;">Standard Playback</div>';
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
    playMedia: async function(media) {
        try {
            const playlistUrl = media.provider === 'prime'
                ? `https://net52.cc/mobile/pv/playlist.php?id=${media.id}`
                : `https://net52.cc/mobile/playlist.php?id=${media.id}`;

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
        grid.innerHTML = '<div style="font-size:18px;">Searching...</div>';

        try {
            const res = await fetch(`https://net52.cc/mobile/search.php?s=${encodeURIComponent(query)}`);
            const data = await res.json();
            grid.innerHTML = '';

            if (data.searchResult && data.searchResult.length) {
                data.searchResult.forEach(item => {
                    const card = document.createElement('div');
                    card.className = 'card focusable';
                    card.innerHTML = `
                        <img src="https://imgcdn.kim/poster/v/${item.id}.jpg" loading="lazy" alt="${item.t}"/>
                        <div class="card-title">${item.t}</div>
                    `;
                    card.onclick = () => {
                        this.openDetails({
                            id: item.id,
                            provider: 'netflix',
                            title: item.t,
                            year: item.y || '',
                            poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`
                        });
                    };
                    grid.appendChild(card);
                });
            } else {
                grid.innerHTML = '<div style="color:#aaa;">No matches found.</div>';
            }
        } catch (e) {
            grid.innerHTML = `<div>Search error: ${e.message}</div>`;
        }
    }
};

window.App = App;
window.onload = () => App.init();
