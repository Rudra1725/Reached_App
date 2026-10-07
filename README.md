+-----------------------------------------------------------------------------------+
 |                             REACHED ARCHITECTURE FLOW                             |
 +-----------------------------------------------------------------------------------+

   +------------------------------------+
   |         USER CONFIGURATION         |
   | • Destination Coordinates (Lat/Lng)|
   | • Custom Message ("P" / text)      |
   | • Recipient Phone Number           |
   | • Daily Auto-Arm Schedule (8:00 AM)|
   +-----------------+------------------+
                     |
                     v
   +-----------------+------------------+
   |          SHARED PREFERENCES        | <-------- Stores config locally
   +-----------------+------------------+
                     |
                     v
   +-----------------+------------------+
   |         DAILY AUTO-TRIGGER         |
   |       (Android WorkManager)        | <-------- Wakes up daily at 8:00 AM
   +-----------------+------------------+
                     |
                     v
   +-----------------+------------------+
   |        REGISTER GEOFENCE           |
   | (Google Play Services Location API)| <-------- Arms 500m campus boundary
   +-----------------+------------------+
                     |
                     v (In pocket during commute)
   +-----------------+------------------+
   |        ENTER GEOFENCE AREA         |
   |    (Crosses Campus Perimeter)      |
   +-----------------+------------------+
                     |
                     v
   +-----------------+------------------+
   |        GEOFENCE RECEIVER           |
   |       (BroadcastReceiver)          |
   +--------+------------------+--------+
            |                  |
            |                  v
            |  +---------------+----------------+
            |  |         DISPATCH SMS           |
            |  |  • Custom automated message    |
            |  |  • Sent via SmsManager to Baba |
            |  +---------------+----------------+
            |                  |
            v                  v
   +--------+------------------+--------+
   |         SELF-DISARM & SLEEP        |
   |  • Unregisters Geofence Client     |
   |  • Cancels Ongoing Notification    |
   |  • Consumes 0% battery rest of day |
   +------------------------------------+
