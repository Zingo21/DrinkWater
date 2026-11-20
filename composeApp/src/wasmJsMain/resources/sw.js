// Shows the server's pushes as notifications, also while the app is closed. Every push must show one:
// browsers, and Safari in particular, stop delivering pushes to apps that receive them silently.
self.addEventListener('push', function (event) {
    var data = {};
    try {
        data = event.data ? event.data.json() : {};
    } catch (e) {
        data = { body: event.data ? event.data.text() : '' };
    }
    var options = { body: data.body || '', icon: 'icon-192.png' };
    // Reminders carry a tag, so a new one replaces the last instead of piling up, and still makes a sound.
    if (data.tag) {
        options.tag = data.tag;
        options.renotify = true;
    }
    event.waitUntil(self.registration.showNotification(data.title || 'DrinkWater', options));
});

// Tapping a notification brings the app forward, or opens it if it isn't running.
self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (windows) {
        if (windows.length > 0) return windows[0].focus();
        return self.clients.openWindow('./');
    }));
});
