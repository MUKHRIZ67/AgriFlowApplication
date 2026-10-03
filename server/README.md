# AgriFlow Chatbot Backend Server (Google Gemini API)

This backend server handles chatbot requests for the AgriFlow Farm Assistant using the official Google Gemini Developer API.

## 📋 Requirements & Setup

### 1. Create a Free Google AI Studio API Key
1. Visit [Google AI Studio](https://aistudio.google.com/).
2. Sign in with your Google Account.
3. Click **Get API key** and click **Create API Key**.
4. Copy your new API key.

> **💡 Note on Gemini Free Tier Quota:**
> The Google AI Studio Gemini Free Tier (e.g., model `gemini-1.5-flash`) includes free request and token limits (15 Requests Per Minute / 1,500 Requests Per Day / 1 Million Tokens Per Minute). It is **100% free** as long as your usage remains within these allowed quota limits.

---

### 2. Configure Environment Variables
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
   *(or create a `.env` file manually in `server/` or the project root directory)*

2. Open `.env` and paste your Google AI Studio API key:
   ```env
   GEMINI_API_KEY=your_actual_google_ai_studio_api_key_here
   PORT=3000
   ```

> **🔒 Security Reminder:** Never commit your actual `.env` file or hard-code `GEMINI_API_KEY` in Git repositories or client code.

---

### 3. Start the Backend Server

```bash
# Navigate to server directory
cd server

# Install dependencies
npm install

# Start the server
npm start
```

The server will start on `http://localhost:3000`.

---

### 💬 API Endpoint

#### `POST /api/chat`

**Request Body:**
```json
{
  "message": "Analyze my farm",
  "context": "Optional farm telemetry context..."
}
```

**Response Format (200 OK):**
```json
{
  "reply": "AI response text here"
}
```

**Error Handling:**
* **400 Bad Request:** Missing message or prompt.
* **429 Too Many Requests:** Gemini Free Tier rate limit reached.
* **500 Internal Server Error:** Missing `GEMINI_API_KEY` or Gemini API failure.
