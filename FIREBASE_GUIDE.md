# Firebase Data Viewing Guide

## How to View User-Submitted Toilet Data

### 1. Access Firebase Console
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project (ToiletSEWA)
3. Navigate to **Firestore Database** in the left sidebar

### 2. View Submitted Data
- **Collection**: `toilets`
- **Documents**: Each toilet submission is a document with auto-generated ID
- **Fields**: All the data submitted by users

### 3. Data Structure
Each toilet document contains:
```json
{
  "id": "auto-generated-id",
  "name": "Public Toilet - Connaught Place",
  "address": "Connaught Place, New Delhi",
  "latitude": 28.6139,
  "longitude": 77.2090,
  "placeType": "PUBLIC_TOILET",
  "isPublic": true,
  "isFree": true,
  "isGenderNeutral": false,
  "isBabyFriendly": true,
  "isDogFriendly": false,
  "isWheelchairAccessible": true,
  "hasChangingTable": true,
  "hasPaper": true,
  "hasSoap": true,
  "hasHandDryer": false,
  "hasRunningWater": true,
  "hasShower": false,
  "cleanlinessRating": 4.0,
  "availabilityRating": 5.0,
  "rating": 4.5,
  "submittedBy": "Anonymous",
  "submittedAt": "2024-01-15T10:30:00Z",
  "lastUpdated": "2024-01-15T10:30:00Z"
}
```

### 4. Real-time Updates
- Data appears in Firestore immediately when users submit
- Real-time listeners in the app update the map instantly
- No manual refresh needed

### 5. Query Examples
You can run queries in Firebase Console:

**Find toilets by location:**
```javascript
// Find toilets within 5km of a point
db.collection("toilets")
  .where("latitude", ">=", 28.6)
  .where("latitude", "<=", 28.7)
  .where("longitude", ">=", 77.2)
  .where("longitude", "<=", 77.3)
```

**Find gender-neutral toilets:**
```javascript
db.collection("toilets")
  .where("isGenderNeutral", "==", true)
```

**Find wheelchair accessible toilets:**
```javascript
db.collection("toilets")
  .where("isWheelchairAccessible", "==", true)
```

### 6. Export Data
1. In Firestore Console, click on the `toilets` collection
2. Click **Export** button
3. Choose format (JSON, CSV)
4. Download the file

### 7. Analytics
- **Total Submissions**: Count documents in `toilets` collection
- **Popular Areas**: Group by location coordinates
- **Amenity Usage**: Count amenities across submissions
- **Rating Trends**: Average ratings over time

### 8. Security Rules
Current rules allow read/write for all users. For production:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /toilets/{document} {
      allow read: if true;
      allow write: if request.auth != null;
    }
  }
}
```

### 9. Backup
- Firebase automatically backs up data
- You can also export manually for additional safety
- Consider setting up automated exports to Google Cloud Storage

### 10. Monitoring
- **Firebase Console**: Real-time usage and errors
- **Crashlytics**: App crashes and performance
- **Analytics**: User behavior and app usage

## Troubleshooting

**Data not appearing in app:**
1. Check internet connection
2. Verify Firebase configuration
3. Check app logs for errors
4. Ensure Firestore rules allow read access

**Submission failed:**
1. Check Firebase Console for errors
2. Verify all required fields are filled
3. Check app logs for validation errors
4. Ensure Firestore rules allow write access

**Real-time updates not working:**
1. Check Firebase connection
2. Verify listener implementation
3. Check app permissions
4. Restart the app 

## 🔧 **Fix the Firestore Index Issue**

### **Step 1: Create the Required Index**

1. **Click this direct link** to create the index:
   ```
   https://console.firebase.google.com/v1/r/project/toiletsewa-296dc/firestore/indexes?create_composite=ClBwcm9qZWN0cy90b2lsZXRzZXdhLTI5NmRjL2RhdGFiYXNlcy8oZGVmYXVsdCkvY29sbGVjdGlvbkdyb3Vwcy90b2lsZXRzL2luZGV4ZXMvXxABGgwKCGxhdGl0dWRlEAEaDQoJbG9uZ2l0dWRlEAEaDAoIX19uYW1lX18QAQ
   ```

2. **Or manually create it:**
   - Go to Firebase Console → Firestore Database → **Indexes** tab
   - Click **"Create Index"**
   - Set these fields:
     - **Collection ID**: `toilets`
     - **Fields to index**:
       - `latitude` (Ascending)
       - `longitude` (Ascending)
       - `__name__` (Ascending)

### **Step 2: Wait for Index to Build**
- The index will take a few minutes to build
- You'll see "Building" status initially
- Wait until it shows "Enabled"

### **Step 3: Alternative Quick Fix**

While the index is building, let me create a temporary fix that will work immediately: 