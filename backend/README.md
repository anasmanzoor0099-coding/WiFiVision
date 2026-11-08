WiFiVision Backend

Network Intelligence & Connectivity Analysis Platform

WiFiVision is a backend service designed to collect, analyze and store wireless network observations and internet performance measurements. It provides network quality insights, geographic heatmap data and historical performance analytics.

Technology Stack

- Runtime: Node.js
- Framework: Express.js
- Database: MongoDB Atlas
- ODM: Mongoose
- Validation: Zod
- Security: Helmet, CORS and Express Rate Limit
- Testing: Node.js Test Runner and Supertest

Core Features

- Wi-Fi network observation management
- Signal strength and network quality analysis
- Geographic heatmap and network density data
- Internet download and upload speed measurements
- HTTP-based latency and jitter statistics
- Application-level request failure measurements
- Historical performance records and analytics
- Input validation and API security controls

Getting Started

Requirements

- Node.js 20 or later
- npm
- MongoDB Atlas connection

Installation

npm install

Environment Configuration

Create a ".env" file in the backend root directory:

PORT=5000
NODE_ENV=development
CORS_ORIGIN=http://localhost:3000
MONGODB_URI=your_mongodb_connection_string

Never commit ".env" or expose database credentials.

Run the Server

Development:

npm run dev

Production:

npm start

The backend runs at:

"http://localhost:5000"

API Endpoints

Method| Endpoint| Description
GET| "/api/health"| Service and database status
GET| "/api/observations"| Retrieve network observations
POST| "/api/observations"| Save a network observation
GET| "/api/observations/stats"| Network statistics
GET| "/api/observations/heatmap"| Geographic heatmap data
GET| "/api/observations/density"| Network density analysis
GET| "/api/quality"| Network quality analysis
GET| "/api/performance"| Run a performance test
GET| "/api/performance/history"| Performance history
GET| "/api/performance/analytics"| Performance analytics
GET| "/api/performance/trends"| Historical performance trends

Testing

Run the automated test suite:

npm test

The test suite covers API functionality, input validation, network quality calculations, database operations and performance statistics.

Measurement Notes

- Latency and jitter are based on HTTP response timings, not ICMP ping.
- Request failure measurements represent HTTP application-level failures, not true network-layer packet loss.
- Network quality scores are application-defined estimates.
- Geographic heatmaps represent collected observations, not guaranteed coverage.

Project Structure

backend/
├── src/
│   ├── config/
│   ├── controllers/
│   ├── middleware/
│   ├── models/
│   ├── routes/
│   ├── services/
│   ├── utils/
│   └── validators/
├── tests/
├── .env.example
├── .gitignore
├── app.js
├── server.js
└── package.json

---

WiFiVision
Turning network observations into connectivity intelligence.