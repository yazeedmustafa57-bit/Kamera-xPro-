const fs = require('fs');
const path = require('path');

const DB_FILE = path.join(__dirname, '..', 'data.json');

function loadDB() {
  try {
    if (fs.existsSync(DB_FILE)) {
      return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
    }
  } catch (e) {}
  return { users: [], cameras: [], events: [] };
}

function saveDB(data) {
  fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2));
}

function getUsers() { return loadDB().users; }
function getCameras() { return loadDB().cameras; }
function getEvents() { return loadDB().events; }

function addUser(user) {
  const db = loadDB();
  db.users.push(user);
  saveDB(db);
  return user;
}

function findUserByEmail(email) {
  return loadDB().users.find(u => u.email === email);
}

function findUserById(id) {
  return loadDB().users.find(u => u.id === id);
}

function addCamera(camera) {
  const db = loadDB();
  db.cameras.push(camera);
  saveDB(db);
  return camera;
}

function getCamerasByOwner(ownerId) {
  return loadDB().cameras.filter(c => c.owner_id === ownerId);
}

function findCameraById(id) {
  return loadDB().cameras.find(c => c.id === id);
}

function findCameraByPairingCode(code) {
  return loadDB().cameras.find(c => c.pairing_code === code);
}

function updateCamera(id, updates) {
  const db = loadDB();
  const cam = db.cameras.find(c => c.id === id);
  if (cam) { Object.assign(cam, updates); saveDB(db); }
  return cam;
}

function deleteCamera(id) {
  const db = loadDB();
  db.cameras = db.cameras.filter(c => c.id !== id);
  saveDB(db);
}

function addEvent(event) {
  const db = loadDB();
  db.events.push(event);
  saveDB(db);
  return event;
}

function getEventsByOwner(ownerId) {
  const db = loadDB();
  const cameras = db.cameras.filter(c => c.owner_id === ownerId);
  const cameraIds = cameras.map(c => c.id);
  return db.events.filter(e => cameraIds.includes(e.camera_id)).sort((a, b) => b.created_at - a.created_at).slice(0, 100);
}

module.exports = { loadDB, saveDB, getUsers, getCameras, getEvents, addUser, findUserByEmail, findUserById, addCamera, getCamerasByOwner, findCameraById, findCameraByPairingCode, updateCamera, deleteCamera, addEvent, getEventsByOwner };
