# ToiletSEWA - Public Bathroom Finder

A comprehensive Android app for finding public bathrooms with real-time user submissions and Firebase backend integration.

## Features

### 🗺️ Interactive Map
- Real-time location tracking
- Search for nearby bathrooms within customizable radius (0.5km - 20km)
- Multiple data sources: Google Places, Refuge Restrooms, and user submissions
- Advanced filtering by amenities (gender-neutral, baby-friendly, wheelchair accessible, etc.)
- Navigation with turn-by-turn directions

### 📝 User Submissions
- **Easy Submission Form**: Users can submit new toilet locations with detailed information
- **Real-time Updates**: New submissions appear on the map instantly
- **Comprehensive Details**: Include amenities like gender-neutral, baby-friendly, wheelchair accessible, etc.
- **Location Picker**: Interactive map to select exact location
- **Current Location**: Auto-fill coordinates from user's current location

### 🔥 Firebase Backend
- **Firestore Database**: Stores all user-submitted toilet locations
- **Real-time Sync**: Live updates when new toilets are added
- **Offline Support**: Works even without internet connection
- **Scalable**: Handles multiple users and locations efficiently

### 🎯 Smart Filtering
- Filter by distance, rating, and amenities
- Gender-neutral bathrooms
- Baby-friendly with changing tables
- Dog-friendly locations
- Wheelchair accessible facilities
- Free vs paid options

### 🧭 Navigation
- Turn-by-turn directions
- Multiple transport modes (driving, walking, cycling)
- Real-time location tracking during navigation
- Route optimization

## Technical Stack

- **Frontend**: Jetpack Compose, Material Design 3
- **Maps**: Google Maps API
- **Backend**: Firebase Firestore
- **Location**: Google Play Services Location
- **Navigation**: Google Directions API
- **Data Sources**: 
  - Google Places API (ratings, reviews)
  - Refuge Restrooms API (detailed amenities)
  - User submissions (Firebase)

## Getting Started

1. **Clone the repository**
2. **Add Firebase Configuration**:
   - Create a Firebase project
   - Download `google-services.json` and place it in the `app/` directory
   - Enable Firestore Database in Firebase Console

3. **Add API Keys**:
   - Google Maps API key in `local.properties`:
     ```
     MAPS_API_KEY=your_google_maps_api_key
     ```
   - Google Places API key (same as Maps API key)

4. **Build and Run**:
   ```bash
   ./gradlew assembleDebug
   ```

## User Submission Flow

1. **Access Submission Form**: Tap the "+" button on the map screen
2. **Fill Basic Information**: Name, address, coordinates
3. **Select Amenities**: Check available facilities
4. **Add Rating**: Rate the bathroom (optional)
5. **Submit**: Location appears on map in real-time

## Real-time Features

- **Live Updates**: New submissions appear instantly
- **Notification System**: Shows when new toilets are added
- **Offline Support**: Submissions work without internet
- **Location Services**: Automatic coordinate detection

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please open an issue on GitHub or contact the development team. 