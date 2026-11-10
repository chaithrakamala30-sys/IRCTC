importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-messaging-compat.js');


const firebaseConfig = {
    apiKey: "AIzaSyBunNystKM7V1TVbxY92xk9OfRgPt5GqIE",
    authDomain: "railconnect-fcm.firebaseapp.com",
    projectId: "railconnect-fcm",
    storageBucket: "railconnect-fcm.firebasestorage.app",
    messagingSenderId: "714510730039",
    appId: "1:714510730039:web:0632a65ab3a2aca44610b2",
    measurementId: "G-K2ETDVHMSE"
};

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
    const notificationTitle = payload.notification?.title || "RailConnect Alert";
    const notificationOptions = {
        body: payload.notification?.body,
        icon: "/favicon.ico",
        data: { url: payload.data?.deepLink || "/notifications" }
    };

    self.registration.showNotification(notificationTitle, notificationOptions);
});