const categories = ['Food', 'Museum', 'Outdoor', 'Shopping', 'Coffee', 'Attraction'];

const fallbackActivities = [
  { id: 'rom', name: 'Royal Ontario Museum', category: 'MUSEUM', rating: 4.7, address: '100 Queens Park', latitude: 43.6677, longitude: -79.3948, durationMinutes: 120, type: 'INDOOR', weatherRisk: 'Low' },
  { id: 'cn-tower', name: 'CN Tower', category: 'ATTRACTION', rating: 4.6, address: '290 Bremner Blvd', latitude: 43.6426, longitude: -79.3871, durationMinutes: 90, type: 'MIXED', weatherRisk: 'Low' },
  { id: 'islands', name: 'Toronto Islands', category: 'OUTDOOR', rating: 4.8, address: 'Toronto Islands', latitude: 43.6214, longitude: -79.3789, durationMinutes: 180, type: 'OUTDOOR', weatherRisk: 'High' },
  { id: 'pai', name: 'Pai Northern Thai Kitchen', category: 'FOOD', rating: 4.5, address: '18 Duncan St', latitude: 43.6477, longitude: -79.3886, durationMinutes: 60, type: 'INDOOR', weatherRisk: 'Low' },
  { id: 'kensington', name: 'Kensington Market', category: 'SHOPPING', rating: 4.4, address: 'Kensington Market', latitude: 43.6545, longitude: -79.4005, durationMinutes: 90, type: 'OUTDOOR', weatherRisk: 'Medium' },
  { id: 'ago', name: 'Art Gallery of Ontario', category: 'MUSEUM', rating: 4.7, address: '317 Dundas St W', latitude: 43.6536, longitude: -79.3925, durationMinutes: 120, type: 'INDOOR', weatherRisk: 'Low' },
  { id: 'balzacs', name: "Balzac's Coffee", category: 'COFFEE', rating: 4.3, address: '1 Trinity St', latitude: 43.6503, longitude: -79.3596, durationMinutes: 45, type: 'INDOOR', weatherRisk: 'Low' }
];

const exactFallbackEvents = [
  { id: 'e-rom', eventType: 'ACTIVITY', startTime: '10:00', endTime: '12:00', notes: 'Auto scheduled', activity: fallbackActivities[0] },
  { id: 'e-travel-1', eventType: 'TRAVEL', startTime: '12:00', endTime: '12:25', notes: 'Travel · 25 min', activity: null },
  { id: 'e-pai', eventType: 'ACTIVITY', startTime: '12:30', endTime: '13:30', notes: 'Auto scheduled', activity: fallbackActivities[3] },
  { id: 'e-travel-2', eventType: 'TRAVEL', startTime: '13:30', endTime: '14:00', notes: 'Travel · 30 min', activity: null },
  { id: 'e-cn', eventType: 'ACTIVITY', startTime: '14:00', endTime: '15:30', notes: 'Auto scheduled', activity: fallbackActivities[1] }
];

const state = {
  activities: fallbackActivities,
  trip: {
    id: 'offline-demo', destination: 'Toronto', date: '2026-07-18', startTime: '10:00', endTime: '19:00',
    transportationMode: 'WALKING', bookmarks: [fallbackActivities[0], fallbackActivities[3], fallbackActivities[1]],
    events: exactFallbackEvents
  },
  selectedId: 'rom', selectedCategory: '', activeTab: 'search', apiOnline: true,
  map: null, markerLayer: null
};

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const icon = name => `<svg class="icon" aria-hidden="true"><use href="#icon-${name}"></use></svg>`;

async function api(path, options = {}) {
  const response = await fetch(path, { headers: { 'Content-Type': 'application/json' }, ...options });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Request failed');
  return data;
}

async function initialize() {
  bindStaticEvents();
  renderCategories();
  initLeafletMap();
  renderAll();
  try {
    state.activities = await api('/api/activities');
    let trip = await api('/api/trips', {
      method: 'POST',
      body: JSON.stringify({ destination: 'Toronto', date: '2026-07-18', startTime: '10:00', endTime: '19:00', transportationMode: 'WALKING' })
    });
    for (const id of ['rom', 'pai', 'cn-tower']) {
      trip = await api(`/api/trips/${trip.id}/bookmarks/${id}`, { method: 'POST' });
    }
    try { trip = await api(`/api/trips/${trip.id}/plan/autoschedule`, { method: 'POST' }); } catch (_) { /* fallback remains usable */ }
    state.trip = trip;
    state.apiOnline = true;
  } catch (_) {
    state.apiOnline = false;
    showToast('Running in offline mock mode');
  }
  renderAll();
}

function initLeafletMap() {
  state.map = L.map('map', { zoomControl: false, attributionControl: true }).setView([43.6532, -79.3832], 13);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(state.map);
  state.markerLayer = L.layerGroup().addTo(state.map);
}

function numberIcon(index, isSelected) {
  const color = isSelected ? '#f06451' : '#0a2142';
  const size = isSelected ? 38 : 32;
  return L.divIcon({
    className: '',
    html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${color};color:#fff;display:grid;place-items:center;font-weight:700;font-size:13px;border:3px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.3);">${index + 1}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2]
  });
}

function renderLeafletMarkers() {
  if (!state.map || !state.markerLayer) return;
  state.markerLayer.clearLayers();
  const bounds = [];
  state.activities.forEach((activity, index) => {
    const isSelected = state.selectedId === activity.id;
    const marker = L.marker([activity.latitude, activity.longitude], {
      icon: numberIcon(index, isSelected),
      zIndexOffset: isSelected ? 1000 : 0
    });
    marker.bindPopup(`<strong>${escapeHtml(activity.name)}</strong><br><small>${escapeHtml(activity.address)}</small>`);
    marker.on('click', () => selectActivity(activity.id));
    state.markerLayer.addLayer(marker);
    bounds.push([activity.latitude, activity.longitude]);
  });
  if (bounds.length > 0) {
    state.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
  }
  updateSelectionPopup();
}

function updateSelectionPopup() {
  const selected = state.activities.find(a => a.id === state.selectedId) || state.activities[0];
  if (!selected) return;
  const index = state.activities.indexOf(selected) + 1;
  $('#mapSelection').innerHTML = `<span class="selection-index">${index}</span><div><strong>${escapeHtml(selected.name)}</strong><small>${escapeHtml(selected.address)}</small></div>`;
}

function bindStaticEvents() {
  $$('.tab').forEach(tab => tab.addEventListener('click', () => switchTab(tab.dataset.tab)));
  $('#searchInput').addEventListener('input', handleSearchInput);
  $('#clearSearch').addEventListener('click', () => { $('#searchInput').value = ''; renderActivities(); $('#searchInput').focus(); });
  $('#ratingFilter').addEventListener('change', renderActivities);
  $('#openFilter').addEventListener('change', renderActivities);
  $('#typeFilter').addEventListener('change', renderActivities);
  $('#autoScheduleButton').addEventListener('click', autoSchedule);
  $('#bookmarkAutoButton').addEventListener('click', autoSchedule);
  $('#openCalendarButton').addEventListener('click', openCalendar);
  $$('[data-close-calendar]').forEach(element => element.addEventListener('click', closeCalendar));
  $('#calendarEditButton').addEventListener('click', () => { closeCalendar(); switchTab('plan'); showToast('Use each event menu to edit a time'); });
  $('#shareHeaderButton').addEventListener('click', shareTrip);
  $('#sharePlanButton').addEventListener('click', shareTrip);
  $('#calendarShareButton').addEventListener('click', shareTrip);
  $('#clearPlanButton').addEventListener('click', clearPlan);
  $('#editPlanButton').addEventListener('click', () => showToast('Choose an event\'s ••• menu to edit or remove it'));
  $('#newTripButton').addEventListener('click', () => { switchTab('options'); $('#optionsPanel input').focus(); });
  $('#tripForm').addEventListener('submit', saveTripOptions);
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && !$('#calendarModal').hidden) closeCalendar(); });
}

let searchDebounce;
function handleSearchInput() {
  clearTimeout(searchDebounce);
  const query = $('#searchInput').value.trim();
  if (query.length >= 2) {
    searchDebounce = setTimeout(() => nominatimSearch(query), 400);
  } else {
    renderActivities();
  }
}

async function nominatimSearch(query) {
  try {
    const bounds = state.map.getBounds();
    const viewbox = `${bounds.getWest()},${bounds.getSouth()},${bounds.getEast()},${bounds.getNorth()}`;
    const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query + ' Toronto')}&format=json&viewbox=${viewbox}&bounded=1&limit=15&addressdetails=1`;
    const response = await fetch(url, { headers: { 'User-Agent': 'CloseAI-TripPlanner/1.0' } });
    const results = await response.json();
    if (!results || results.length === 0) {
      renderActivities();
      return;
    }
    const places = results.map(mapNominatimResult).filter(Boolean);
    state.activities = places;
    syncPlacesToBackend(places);
    renderLeafletMarkers();
    renderActivities();
  } catch (_) {
    renderActivities();
  }
}

function mapNominatimResult(result) {
  if (!result.place_id || !result.lat || !result.lon) return null;
  const lat = parseFloat(result.lat);
  const lng = parseFloat(result.lon);
  if (!isFinite(lat) || !isFinite(lng)) return null;
  const types = result.type ? [result.type] : [];
  const category = mapCategory(result, types);
  const indoorType = mapIndoorType(result, types);
  const address = result.display_name || '';
  const shortName = result.namedetails && result.namedetails.name ? result.namedetails.name
          : result.display_name ? result.display_name.split(',')[0] : 'Unknown place';
  return {
    id: String(result.place_id),
    name: shortName,
    category,
    rating: 4.0 + Math.random() * 0.8,
    address,
    latitude: lat,
    longitude: lng,
    durationMinutes: estimateDuration(category),
    type: indoorType,
    weatherRisk: indoorType === 'OUTDOOR' ? 'High' : indoorType === 'MIXED' ? 'Medium' : 'Low'
  };
}

function mapCategory(result, types) {
  const classType = (result.class || '').toLowerCase();
  const osmType = (result.type || '').toLowerCase();
  if (['restaurant', 'cafe', 'bar', 'pub', 'fast_food', 'food_court'].includes(osmType) || classType === 'amenity') return 'FOOD';
  if (['museum', 'gallery', 'arts_centre'].includes(osmType) || classType === 'tourism') {
    if (osmType === 'museum' || osmType === 'gallery') return 'MUSEUM';
    return 'ATTRACTION';
  }
  if (['park', 'garden', 'nature_reserve'].includes(osmType) || classType === 'leisure') return 'OUTDOOR';
  if (['shop', 'supermarket', 'mall'].includes(osmType) || classType === 'shop') return 'SHOPPING';
  if (osmType === 'cafe' || osmType === 'coffee') return 'COFFEE';
  if (classType === 'tourism') return 'ATTRACTION';
  return 'ATTRACTION';
}

function mapIndoorType(result, types) {
  const classType = (result.class || '').toLowerCase();
  const osmType = (result.type || '').toLowerCase();
  const indoor = ['restaurant', 'cafe', 'bar', 'pub', 'museum', 'gallery', 'shop', 'supermarket'];
  const outdoor = ['park', 'garden', 'nature_reserve', 'attraction'];
  if (indoor.includes(osmType) || classType === 'shop') return 'INDOOR';
  if (outdoor.includes(osmType) || classType === 'leisure') return 'OUTDOOR';
  if (classType === 'tourism') return 'MIXED';
  return 'MIXED';
}

function estimateDuration(category) {
  switch (category) {
    case 'FOOD': return 60;
    case 'MUSEUM': return 120;
    case 'OUTDOOR': return 120;
    case 'SHOPPING': return 90;
    case 'COFFEE': return 30;
    case 'ATTRACTION': return 90;
    default: return 60;
  }
}

async function syncPlacesToBackend(places) {
  if (!state.apiOnline) return;
  try {
    await api('/api/places/search', {
      method: 'POST',
      body: JSON.stringify({ places })
    });
  } catch (_) { /* backend sync is best-effort */ }
}

function switchTab(name) {
  state.activeTab = name;
  $$('.tab').forEach(tab => {
    const active = tab.dataset.tab === name;
    tab.classList.toggle('active', active);
    tab.setAttribute('aria-selected', String(active));
  });
  $$('.tab-panel').forEach(panel => panel.classList.toggle('active', panel.id === `${name}Panel`));
  if (name === 'plan') renderPlan();
  if (name === 'bookmarks') renderBookmarks();
  if (name === 'options') syncTripForm();
  if (state.map) setTimeout(() => state.map.invalidateSize(), 50);
}

function renderCategories() {
  $('#categoryFilters').innerHTML = categories.map(category =>
    `<button class="filter-chip" data-category="${category.toUpperCase()}">${category}</button>`).join('');
  $$('.filter-chip').forEach(button => button.addEventListener('click', () => {
    state.selectedCategory = state.selectedCategory === button.dataset.category ? '' : button.dataset.category;
    $$('.filter-chip').forEach(item => item.classList.toggle('active', item.dataset.category === state.selectedCategory));
    renderActivities();
  }));
}

function getFilteredActivities() {
  const query = $('#searchInput').value.trim().toLowerCase();
  const rating = Number($('#ratingFilter').value);
  const type = $('#typeFilter').value;
  return state.activities.filter(activity => {
    const matchesText = !query || activity.name.toLowerCase().includes(query) || activity.category.toLowerCase().includes(query);
    return matchesText && (!state.selectedCategory || activity.category === state.selectedCategory)
      && activity.rating >= rating && (!type || activity.type === type);
  });
}

function isBookmarked(id) { return state.trip.bookmarks.some(activity => activity.id === id); }
function isPlanned(id) { return state.trip.events.some(event => event.activity && event.activity.id === id); }

function activityCard(activity, index, compact = false) {
  const selected = state.selectedId === activity.id;
  const bookmarked = isBookmarked(activity.id);
  return `<article class="activity-card ${selected ? 'selected' : ''}" data-select-activity="${activity.id}">
    <div class="activity-top">
      <span class="activity-number">${index + 1}</span>
      <div class="activity-title"><h3>${escapeHtml(activity.name)}</h3><p><span class="category">${activity.category.toLowerCase()}</span><span class="rating">${activity.rating.toFixed(1)}</span><span>${escapeHtml(activity.address.length > 40 ? activity.address.substring(0, 40) + '...' : activity.address)}</span></p></div>
      <button class="bookmark-icon ${bookmarked ? 'bookmarked' : ''}" data-bookmark="${activity.id}" aria-label="${bookmarked ? 'Remove bookmark' : 'Bookmark'} ${escapeHtml(activity.name)}">${icon('bookmark')}</button>
    </div>
    <div class="activity-meta"><span>${formatDuration(activity.durationMinutes)}</span><span>${titleCase(activity.type)}</span><span>${activity.weatherRisk} weather risk</span></div>
    ${compact ? '' : `<div class="activity-actions"><button class="button ghost" data-bookmark="${activity.id}">${bookmarked ? 'Saved' : 'Bookmark'}</button><button class="button primary" data-add="${activity.id}" ${isPlanned(activity.id) ? 'disabled' : ''}>${icon('plus')}${isPlanned(activity.id) ? 'In plan' : 'Add to plan'}</button></div>`}
  </article>`;
}

function renderActivities() {
  const items = getFilteredActivities();
  $('#resultCount').textContent = `${items.length} ${items.length === 1 ? 'place' : 'places'}`;
  $('#activityList').innerHTML = items.length ? items.map((item) => activityCard(item, state.activities.indexOf(item))).join('')
    : `<div class="empty-state"><strong>No places match</strong>Try clearing a category or rating filter.</div>`;
  bindActivityEvents($('#activityList'));
}

function renderBookmarks() {
  const bookmarks = state.trip.bookmarks;
  $('#bookmarkCount').textContent = bookmarks.length;
  $('#bookmarkList').innerHTML = bookmarks.length ? bookmarks.map(activity => activityCard(activity, state.activities.findIndex(item => item.id === activity.id), true)).join('')
    : `<div class="empty-state"><strong>Your shortlist is empty</strong>Save a few places from Search, then auto schedule them.</div>`;
  bindActivityEvents($('#bookmarkList'));
  $('#bookmarkAutoButton').disabled = bookmarks.length === 0;
}

function bindActivityEvents(container) {
  container.querySelectorAll('[data-select-activity]').forEach(card => card.addEventListener('click', event => {
    if (event.target.closest('button')) return;
    selectActivity(card.dataset.selectActivity);
  }));
  container.querySelectorAll('[data-bookmark]').forEach(button => button.addEventListener('click', event => {
    event.stopPropagation(); toggleBookmark(button.dataset.bookmark);
  }));
  container.querySelectorAll('[data-add]').forEach(button => button.addEventListener('click', event => {
    event.stopPropagation(); addToPlan(button.dataset.add);
  }));
}

function selectActivity(id) {
  state.selectedId = id;
  renderLeafletMarkers();
  const activity = state.activities.find(a => a.id === id);
  if (activity && state.map) {
    state.map.setView([activity.latitude, activity.longitude], 15);
  }
  renderActivities();
  const card = $(`[data-select-activity="${CSS.escape(id)}"]`);
  if (card) card.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}

async function toggleBookmark(id) {
  const bookmarked = isBookmarked(id);
  const activity = state.activities.find(item => item.id === id);
  try {
    if (state.apiOnline) state.trip = await api(`/api/trips/${state.trip.id}/bookmarks/${id}`, { method: bookmarked ? 'DELETE' : 'POST' });
    else state.trip.bookmarks = bookmarked ? state.trip.bookmarks.filter(item => item.id !== id) : [...state.trip.bookmarks, activity];
    showToast(bookmarked ? 'Removed from saved places' : 'Saved for your day');
  } catch (error) { showToast(error.message); }
  renderAll();
}

async function addToPlan(id) {
  try {
    if (state.apiOnline) state.trip = await api(`/api/trips/${state.trip.id}/plan/manual`, { method: 'POST', body: JSON.stringify({ activityId: id }) });
    else {
      const activity = state.activities.find(item => item.id === id);
      const last = state.trip.events[state.trip.events.length - 1];
      const start = last ? addMinutes(last.endTime, 15) : state.trip.startTime;
      state.trip.events.push({ id: `local-${Date.now()}`, eventType: 'ACTIVITY', startTime: start, endTime: addMinutes(start, activity.durationMinutes), notes: 'Added manually', activity });
    }
    showToast('Added to Day Plan');
    renderAll();
  } catch (error) { showToast(error.message); }
}

async function autoSchedule() {
  try {
    if (state.apiOnline) state.trip = await api(`/api/trips/${state.trip.id}/plan/autoschedule`, { method: 'POST' });
    else state.trip.events = exactFallbackEvents.filter(event => !event.activity || isBookmarked(event.activity.id));
    switchTab('plan');
    showToast('Your day was auto scheduled');
    renderAll();
  } catch (error) { showToast(error.message); }
}

function renderPlan() {
  const events = state.trip.events;
  $('#dayPlanList').innerHTML = events.length ? events.map(event => {
    const travel = event.eventType === 'TRAVEL';
    return `<article class="plan-event ${travel ? 'travel' : ''}"><time class="plan-time">${formatTime(event.startTime)}</time><span class="plan-rail"><i class="plan-dot"></i></span><div class="plan-content"><strong>${travel ? escapeHtml(event.notes) : escapeHtml(event.activity.name)}</strong><p>${travel ? state.trip.transportationMode.toLowerCase() : `${formatTime(event.startTime)}–${formatTime(event.endTime)} · ${formatDuration(minutesBetween(event.startTime, event.endTime))}`}</p></div><button class="event-menu" data-event-menu="${event.id}" aria-label="Edit ${travel ? 'travel event' : escapeHtml(event.activity.name)}">•••</button></article>`;
  }).join('') : `<div class="empty-state"><strong>No stops planned yet</strong>Add places manually or use Auto Schedule from Bookmarks.</div>`;
  $$('[data-event-menu]').forEach(button => button.addEventListener('click', () => editEvent(button.dataset.eventMenu)));
  const activityMinutes = events.filter(event => event.eventType === 'ACTIVITY').reduce((sum, event) => sum + minutesBetween(event.startTime, event.endTime), 0);
  const stops = events.filter(event => event.eventType === 'ACTIVITY').length;
  $('#planSummary').textContent = `${stops} ${stops === 1 ? 'stop' : 'stops'} · ${formatDuration(activityMinutes)} of activities`;
}

async function editEvent(id) {
  const event = state.trip.events.find(item => item.id === id);
  if (!event) return;
  const choice = window.prompt(`Edit ${event.activity ? event.activity.name : 'travel'}\nEnter a new start time (HH:MM), or type REMOVE`, event.startTime);
  if (!choice) return;
  if (choice.trim().toUpperCase() === 'REMOVE') { await removeEvent(id); return; }
  if (!/^\d{2}:\d{2}$/.test(choice)) { showToast('Use a 24-hour time such as 14:30'); return; }
  const duration = minutesBetween(event.startTime, event.endTime);
  try {
    if (state.apiOnline) state.trip = await api(`/api/trips/${state.trip.id}/plan/${id}`, { method: 'PUT', body: JSON.stringify({ startTime: choice, endTime: addMinutes(choice, duration), notes: event.notes }) });
    else { event.startTime = choice; event.endTime = addMinutes(choice, duration); }
    showToast('Event time updated'); renderAll(); renderCalendar();
  } catch (error) { showToast(error.message); }
}

async function removeEvent(id) {
  try {
    if (state.apiOnline) state.trip = await api(`/api/trips/${state.trip.id}/plan/${id}`, { method: 'DELETE' });
    else state.trip.events = state.trip.events.filter(event => event.id !== id);
    showToast('Event removed'); renderAll(); renderCalendar();
  } catch (error) { showToast(error.message); }
}

async function clearPlan() {
  const ids = state.trip.events.map(event => event.id);
  try {
    if (state.apiOnline) for (const id of ids) state.trip = await api(`/api/trips/${state.trip.id}/plan/${id}`, { method: 'DELETE' });
    else state.trip.events = [];
    showToast('Day Plan cleared'); renderAll();
  } catch (error) { showToast(error.message); }
}

function openCalendar() {
  renderCalendar();
  $('#calendarModal').hidden = false;
  document.body.style.overflow = 'hidden';
  $('.calendar-modal .icon-button').focus();
}
function closeCalendar() { $('#calendarModal').hidden = true; document.body.style.overflow = ''; $('#openCalendarButton').focus(); }

function renderCalendar() {
  const startHour = 9, pixelsPerMinute = 1;
  let html = '';
  for (let hour = 9; hour <= 19; hour++) html += `<div class="time-line" style="top:${(hour - startHour) * 60}px"><span class="time-label">${formatHour(hour)}</span></div>`;
  const colors = ['blue', 'coral', 'teal']; let activityIndex = 0;
  for (const event of state.trip.events) {
    const top = (toMinutes(event.startTime) - startHour * 60) * pixelsPerMinute;
    const height = Math.max(event.eventType === 'TRAVEL' ? 24 : 42, minutesBetween(event.startTime, event.endTime) * pixelsPerMinute);
    if (event.eventType === 'TRAVEL') {
      html += `<div class="calendar-event travel" style="top:${top}px;height:${height}px">${icon('transit')} ${escapeHtml(event.notes)}</div>`;
    } else {
      const color = colors[activityIndex++ % colors.length];
      html += `<div class="calendar-event ${color}" style="top:${top}px;height:${height}px"><strong>${escapeHtml(event.activity.name)}</strong><small>${formatTime(event.startTime)}–${formatTime(event.endTime)}</small><span class="calendar-event-actions"><button data-cal-edit="${event.id}" aria-label="Edit event">${icon('edit')}</button><button data-cal-remove="${event.id}" aria-label="Remove event">${icon('trash')}</button></span></div>`;
    }
  }
  $('#calendarTimeline').innerHTML = html;
  $$('[data-cal-edit]').forEach(button => button.addEventListener('click', () => editEvent(button.dataset.calEdit)));
  $$('[data-cal-remove]').forEach(button => button.addEventListener('click', () => removeEvent(button.dataset.calRemove)));
  const activities = state.trip.events.filter(event => event.eventType === 'ACTIVITY');
  const travel = state.trip.events.filter(event => event.eventType === 'TRAVEL');
  $('#calendarStops').textContent = `${activities.length} stops`;
  $('#calendarActivities').textContent = formatDuration(activities.reduce((sum, event) => sum + minutesBetween(event.startTime, event.endTime), 0));
  $('#calendarTravel').textContent = formatDuration(travel.reduce((sum, event) => sum + minutesBetween(event.startTime, event.endTime), 0));
}

function syncTripForm() {
  const form = $('#tripForm');
  if (!form || !state.trip) return;
  form.destination.value = state.trip.destination || '';
  form.date.value = state.trip.date || '';
  form.startTime.value = state.trip.startTime || '';
  form.endTime.value = state.trip.endTime || '';
  const mode = state.trip.transportationMode || 'WALKING';
  const radio = form.querySelector(`input[name="transportationMode"][value="${mode}"]`);
  if (radio) radio.checked = true;
}

async function saveTripOptions(event) {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  try {
    if (state.apiOnline && state.trip?.id && state.trip.id !== 'offline-demo') {
      state.trip = await api(`/api/trips/${state.trip.id}`, { method: 'PUT', body: JSON.stringify(data) });
    } else if (state.apiOnline) {
      state.trip = await api('/api/trips', { method: 'POST', body: JSON.stringify(data) });
    } else {
      state.trip = { ...state.trip, id: state.trip?.id || `local-${Date.now()}`, ...data };
    }
    $('#tripTitle').textContent = `${data.destination} day trip`;
    $('#headerDate').textContent = new Date(`${data.date}T12:00:00`).toLocaleDateString('en-CA', { month: 'long', day: 'numeric' });
    switchTab('search'); renderAll(); showToast('Itinerary updated');
  } catch (error) { showToast(error.message); }
}

async function shareTrip() {
  let summary = localSummary();
  try { if (state.apiOnline) summary = (await api(`/api/trips/${state.trip.id}/share`)).message; } catch (_) { /* use local summary */ }
  try { await navigator.clipboard.writeText(summary); showToast('Trip summary copied to clipboard'); }
  catch (_) { window.prompt('Copy your trip summary', summary); }
}

function localSummary() {
  return [`CloseAI trip to ${state.trip.destination} on ${state.trip.date}`,
    ...state.trip.events.map(event => `${formatTime(event.startTime)} — ${event.activity ? event.activity.name : event.notes}`)].join('\n');
}

function renderAll() {
  renderLeafletMarkers();
  renderActivities();
  renderBookmarks();
  renderPlan();
}

function formatTime(value) { const [hour, minute] = value.split(':').map(Number); return new Date(2026, 0, 1, hour, minute).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }); }
function formatHour(hour) { return new Date(2026, 0, 1, hour, 0).toLocaleTimeString('en-US', { hour: 'numeric' }); }
function formatDuration(minutes) { if (!minutes) return '0 min'; const hours = Math.floor(minutes / 60), rest = minutes % 60; return `${hours ? `${hours} hr` : ''}${hours && rest ? ' ' : ''}${rest ? `${rest} min` : ''}`; }
function toMinutes(value) { const [hour, minute] = value.split(':').map(Number); return hour * 60 + minute; }
function minutesBetween(start, end) { return toMinutes(end) - toMinutes(start); }
function addMinutes(value, minutes) { const total = toMinutes(value) + minutes; return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`; }
function titleCase(value) { return value.toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()); }
function escapeHtml(value) { return String(value).replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char])); }
let toastTimer;
function showToast(message) { clearTimeout(toastTimer); $('#toast').textContent = message; $('#toast').classList.add('show'); toastTimer = setTimeout(() => $('#toast').classList.remove('show'), 2400); }

initialize();
