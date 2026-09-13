// Ayushflix Desktop Frontend Logic
let currentFeed = null;
let currentDetails = null;
let selectedDubId = null;
let selectedSeason = 1;

document.addEventListener('DOMContentLoaded', () => {
  initNavbar();
  initSearch();
  initModal();
  loadHomeFeed();
});

function initNavbar() {
  window.addEventListener('scroll', () => {
    const navbar = document.querySelector('.navbar');
    if (window.scrollY > 50) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
  });
}

// 1. Home Feed Loading
async function loadHomeFeed() {
  try {
    const res = await fetch('/api/home');
    const data = await res.json();
    currentFeed = data;

    // Render Hero Banner
    if (data.banner) {
      renderHeroBanner(data.banner);
    }

    // Render Rows
    const rowsContainer = document.getElementById('feedRows');
    rowsContainer.innerHTML = '';
    data.rows.forEach(row => {
      const rowEl = createRowElement(row);
      rowsContainer.appendChild(rowEl);
    });
  } catch (err) {
    console.error("Failed to load home feed:", err);
  }
}

function renderHeroBanner(item) {
  document.getElementById('heroTitle').textContent = item.title;
  document.getElementById('heroRating').textContent = `★ ${item.rating || '8.5'}`;
  document.getElementById('heroType').textContent = item.type.toUpperCase();
  if (item.cover) {
    document.getElementById('heroBackdrop').style.backgroundImage = `url('${item.cover}')`;
  }

  document.getElementById('heroPlayBtn').onclick = () => openDetails(item.id, true);
  document.getElementById('heroInfoBtn').onclick = () => openDetails(item.id, false);
}

function createRowElement(row) {
  const rowDiv = document.createElement('div');
  rowDiv.className = 'feed-row';
  rowDiv.id = row.key;

  rowDiv.innerHTML = `
    <h2 class="row-title">${row.title}</h2>
    <div class="carousel-wrapper">
      <button class="carousel-btn prev">&#10094;</button>
      <div class="carousel-container"></div>
      <button class="carousel-btn next">&#10095;</button>
    </div>
  `;

  const container = rowDiv.querySelector('.carousel-container');
  row.items.forEach(item => {
    const card = createCardElement(item);
    container.appendChild(card);
  });

  const prevBtn = rowDiv.querySelector('.carousel-btn.prev');
  const nextBtn = rowDiv.querySelector('.carousel-btn.next');
  prevBtn.onclick = () => container.scrollBy({ left: -450, behavior: 'smooth' });
  nextBtn.onclick = () => container.scrollBy({ left: 450, behavior: 'smooth' });

  return rowDiv;
}

function createCardElement(item) {
  const card = document.createElement('div');
  card.className = 'movie-card';
  card.innerHTML = `
    <img class="card-poster" src="${item.cover || ''}" alt="${item.title}" loading="lazy">
    <div class="card-info">
      <div class="card-title" title="${item.title}">${item.title}</div>
      <div class="card-rating">★ ${item.rating || 'N/A'}</div>
    </div>
  `;
  card.onclick = () => openDetails(item.id);
  return card;
}

// 2. Search Handling
function initSearch() {
  const input = document.getElementById('searchInput');
  const clearBtn = document.getElementById('searchClear');
  const resultsSec = document.getElementById('searchResultsSection');
  const feedRows = document.getElementById('feedRows');
  const heroBanner = document.getElementById('heroBanner');
  let debounceTimer = null;

  input.addEventListener('input', () => {
    const q = input.value.trim();
    if (q.length > 0) {
      clearBtn.style.display = 'block';
    } else {
      clearBtn.style.display = 'none';
      resultsSec.style.display = 'none';
      feedRows.style.display = 'flex';
      heroBanner.style.display = 'flex';
      return;
    }

    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => performSearch(q), 350);
  });

  clearBtn.onclick = () => {
    input.value = '';
    clearBtn.style.display = 'none';
    resultsSec.style.display = 'none';
    feedRows.style.display = 'flex';
    heroBanner.style.display = 'flex';
  };
}

async function performSearch(query) {
  const resultsSec = document.getElementById('searchResultsSection');
  const searchGrid = document.getElementById('searchGrid');
  const feedRows = document.getElementById('feedRows');
  const heroBanner = document.getElementById('heroBanner');
  const heading = document.getElementById('searchHeading');

  heading.textContent = `Search results for "${query}"`;
  resultsSec.style.display = 'block';
  feedRows.style.display = 'none';
  heroBanner.style.display = 'none';

  try {
    const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
    const data = await res.json();
    searchGrid.innerHTML = '';
    if (data.results && data.results.length > 0) {
      data.results.forEach(item => {
        searchGrid.appendChild(createCardElement(item));
      });
    } else {
      searchGrid.innerHTML = '<p style="color:#aaa; font-size:16px;">No titles found.</p>';
    }
  } catch (err) {
    console.error("Search failed:", err);
  }
}

// 3. Details Modal Handling
function initModal() {
  const modal = document.getElementById('detailsModal');
  const closeBtn = document.getElementById('modalClose');
  closeBtn.onclick = () => modal.classList.remove('open');
  modal.onclick = (e) => {
    if (e.target === modal) modal.classList.remove('open');
  };
}

async function openDetails(subjectId, autoPlay = false) {
  try {
    const res = await fetch(`/api/details?id=${subjectId}`);
    const details = await res.json();
    currentDetails = details;
    selectedDubId = details.dubs && details.dubs.length > 0 ? details.dubs[0].subjectId : subjectId;

    document.getElementById('modalTitle').textContent = details.title;
    document.getElementById('modalPoster').src = details.cover || '';
    document.getElementById('modalRating').textContent = `★ ${details.rating || 'N/A'}`;
    document.getElementById('modalYear').textContent = details.year || '2024';
    document.getElementById('modalDuration').textContent = details.duration || '';
    document.getElementById('modalType').textContent = details.type.toUpperCase();
    document.getElementById('modalGenres').textContent = details.genre || '';
    document.getElementById('modalDesc').textContent = details.description || 'No description available.';

    // Render Dubs
    const dubsList = document.getElementById('modalDubsList');
    dubsList.innerHTML = '';
    if (details.dubs && details.dubs.length > 0) {
      document.getElementById('modalDubsContainer').style.display = 'block';
      details.dubs.forEach(d => {
        const pill = document.createElement('div');
        pill.className = `dub-pill ${d.subjectId === selectedDubId ? 'active' : ''}`;
        pill.textContent = d.language;
        pill.onclick = () => {
          selectedDubId = d.subjectId;
          document.querySelectorAll('.dub-pill').forEach(p => p.classList.remove('active'));
          pill.classList.add('active');
        };
        dubsList.appendChild(pill);
      });
    } else {
      document.getElementById('modalDubsContainer').style.display = 'none';
    }

    // Series Seasons & Episodes
    const seriesSec = document.getElementById('seriesSection');
    const playBtn = document.getElementById('modalPlayBtn');
    if (details.type === 'series' && details.seasons && details.seasons.length > 0) {
      seriesSec.style.display = 'block';
      playBtn.textContent = 'Play Season 1 Episode 1';
      renderSeriesSeasons(details.seasons);
      playBtn.onclick = () => launchPlayer(details.title, selectedDubId, 1, 1);
    } else {
      seriesSec.style.display = 'none';
      playBtn.textContent = 'Play Movie';
      playBtn.onclick = () => launchPlayer(details.title, selectedDubId, 0, 0);
    }

    if (autoPlay) {
      if (details.type === 'series') {
        launchPlayer(details.title, selectedDubId, 1, 1);
      } else {
        launchPlayer(details.title, selectedDubId, 0, 0);
      }
      return;
    }

    document.getElementById('detailsModal').classList.add('open');
  } catch (err) {
    console.error("Failed to load details:", err);
  }
}

function renderSeriesSeasons(seasons) {
  const tabsContainer = document.getElementById('seasonTabs');
  const epGrid = document.getElementById('episodesGrid');
  tabsContainer.innerHTML = '';

  selectedSeason = seasons[0].season;
  seasons.forEach((s, idx) => {
    const tab = document.createElement('button');
    tab.className = `season-tab ${idx === 0 ? 'active' : ''}`;
    tab.textContent = `Season ${s.season}`;
    tab.onclick = () => {
      selectedSeason = s.season;
      document.querySelectorAll('.season-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      renderEpisodes(s.episodes);
    };
    tabsContainer.appendChild(tab);
  });

  renderEpisodes(seasons[0].episodes);
}

function renderEpisodes(episodes) {
  const epGrid = document.getElementById('episodesGrid');
  epGrid.innerHTML = '';
  episodes.forEach(ep => {
    const btn = document.createElement('button');
    btn.className = 'ep-btn';
    btn.textContent = `EP ${ep}`;
    btn.onclick = () => {
      launchPlayer(`${currentDetails.title} (S${selectedSeason} E${ep})`, selectedDubId, selectedSeason, ep);
    };
    epGrid.appendChild(btn);
  });
}
