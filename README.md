# Cookie Enumerator - Burp Suite Extension

A Burp Suite extension that helps identify which cookies are truly necessary for a request to succeed. When dealing with requests that have multiple cookies, this extension systematically tests each cookie to determine if it's required or optional.

## Features

### AI-Powered Cookie Classification 🤖
- **Automatic Cookie Analysis**: Uses AI (9 providers supported!) to identify cookie vendors, purposes, and categories
- **Intelligent Caching**: SQLite database stores cookie information for instant lookup
- **Non-Blocking Processing**: Async queue processes cookies without slowing down Burp traffic
- **Custom Editor Tab**: View cookie information directly in the Inspector alongside requests
- **Privacy Assessment**: Automatically categorizes cookies and evaluates privacy impact
- **Vendor Identification**: Recognizes cookies from Google Analytics, Facebook, Stripe, etc.
- **Pattern Matching**: Database supports regex patterns for instant recognition (_ga_* → Google Analytics)
- **Database Management**: Edit, update, and delete cookie entries directly in the UI
- **Manual Corrections**: Override AI classifications and add custom cookie definitions

### Standard Analysis
- **Automated Cookie Testing**: Automatically tests each cookie by removing it individually
- **Baseline Comparison**: Compares responses against a baseline (all cookies included)
- **Smart Analysis**: Detects requirement based on:
  - HTTP status code changes (401, 403, 302, 500, etc.)
  - Response content length variations (>20% change)
  - Overall response differences
- **Fast Results**: Quick analysis with minimal requests (N+1 requests for N cookies)

### Deep Analysis Mode ⚡
- **Minimal Set Discovery**: Finds the TRUE minimum set of cookies required
- **OR Relationship Detection**: Detects alternative cookies (sessionId1 OR sessionId2)
- **Threshold Requirements**: Handles cases where "at least X of Y" cookies are needed
- **Alternative Cookie Detection**: Identifies which cookies can substitute for others
- **Comprehensive Analysis**: Multi-phase algorithm for complex dependencies
- **Detailed Reporting**: Shows minimal sets, alternatives, and total requests sent

### UI Features
- **Visual Results**: Clear, sortable table showing which cookies are required vs optional
- **Request/Response Viewer**: Click any row to view the actual HTTP request and response
- **MINIMAL SET Row**: Final verification showing all required cookies in one request
- **Multi-Select & Copy**: Select multiple cookies and press Ctrl+C to copy names
- **Context Menu Integration**: Right-click on any request to analyze cookies
- **Status Log Window**: Detailed logging in a separate window (click "View Status Log" button)
- **Mode Selection**: Toggle between Standard and Deep Analysis modes
- **Project Persistence**: Analysis results automatically saved to Burp project file

## Installation

### Option 1: Using Pre-built JAR

1. Download `cookie-enum-num.jar` from the releases
2. Open Burp Suite
3. Go to Extensions tab
4. Click "Add"
5. Select the `cookie-enum-num.jar` file
6. Click "Next"
7. The extension should load successfully

### Option 2: Build from Source

#### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher

#### Build Steps

```bash
# Clone the repository
git clone <repository-url>
cd cookie-enum-num

# Build with Maven
mvn clean package

# The JAR will be created at:
# target/cookie-enum-num.jar
```

The Maven build process:
- Downloads all dependencies automatically (Montoya API, SQLite JDBC)
- Compiles all source files
- Creates a fat JAR with all dependencies included
- Output: `target/cookie-enum-num.jar`

## Configuration

### AI-Powered Cookie Classification Setup

The extension now supports **9 different AI providers**! Choose what works best for you:

#### Providers with Automatic Model Discovery ✨

These providers support the `/v1/models` endpoint - the extension automatically queries and populates available models:

##### Option 1: OpenAI

1. **Get API Key**: https://platform.openai.com/api-keys
2. **Configure**:
   - **Provider**: Select "OpenAI"
   - **API Key**: Paste your key
   - **Model**: Automatically populated from OpenAI! Just wait a moment after entering your API key
   - Models include: gpt-4-turbo, gpt-4, gpt-3.5-turbo, etc.

##### Option 2: Mistral AI

1. **Get API Key**: https://console.mistral.ai/
2. **Configure**:
   - **Provider**: Select "Mistral AI"
   - **API Key**: Paste your key
   - **Model**: Auto-populated (mistral-large, mistral-medium, mistral-small, etc.)

##### Option 3: Together AI

1. **Get API Key**: https://api.together.xyz/settings/api-keys
2. **Configure**:
   - **Provider**: Select "Together AI"
   - **API Key**: Paste your key
   - **Model**: Auto-populated with 100+ open source models!

##### Option 4: Groq (Ultra-Fast!)

1. **Get API Key**: https://console.groq.com/keys
2. **Configure**:
   - **Provider**: Select "Groq"
   - **API Key**: Paste your key
   - **Model**: Auto-populated (llama3-70b, mixtral-8x7b, gemma, etc.)
   - Note: Groq is extremely fast with hardware acceleration!

##### Option 5: LM Studio (Local - FREE!)

1. **Install and Setup LM Studio**:
   - Download LM Studio from https://lmstudio.ai/
   - Download a model (recommended: Llama 3 8B, Mistral 7B, or Phi-3)
   - Load the model in LM Studio
   - Go to the "Local Server" tab in LM Studio
   - Click "Start Server" (default port: 1234)

2. **Configure**:
   - **Provider**: Select "LM Studio (Local)"
   - **API Key**: Leave empty
   - **Model**: Auto-populated from your running LM Studio instance!

##### Option 6: Ollama (Local - FREE!)

1. **Install Ollama**: https://ollama.ai/
2. **Start Ollama** with OpenAI compatibility:
   ```bash
   # Set environment variable for OpenAI compatibility
   OLLAMA_ORIGINS=* ollama serve
   ```
3. **Configure**:
   - **Provider**: Select "Ollama (Local)"
   - **API Key**: Leave empty
   - **Model**: Auto-populated from Ollama!

#### Providers with Pre-configured Models 📋

These providers don't have a public models endpoint, so we provide the known model list:

##### Option 7: Anthropic (Claude) ⚠️ Coming Soon

1. **Get API Key**: https://console.anthropic.com/
2. **Configure**:
   - **Provider**: Select "Anthropic (Claude)"
   - **API Key**: Paste your key
   - **Model**: Select from dropdown (claude-3-5-sonnet, claude-3-opus, etc.)

**Note**: Anthropic uses a different API format. Full support requires additional implementation. Currently configured but may not work correctly.

##### Option 8: Azure OpenAI

1. **Setup Azure OpenAI**: https://azure.microsoft.com/en-us/products/ai-services/openai-service
2. **Configure**:
   - **Provider**: Select "Azure OpenAI"
   - **API Endpoint**: Enter your Azure endpoint (e.g., https://YOUR-RESOURCE.openai.azure.com/)
   - **API Key**: Enter your Azure key
   - **Model**: Select your deployed model name

##### Option 9: Custom OpenAI-Compatible

For any other OpenAI-compatible API:

- **Provider**: Select "Custom OpenAI-Compatible"
- **API Endpoint**: Enter your endpoint URL
- **API Key**: Enter if required
- **Model**: Click "Refresh" to auto-discover, or enter manually

---

### How Model Auto-Discovery Works

When you select a provider with auto-discovery:
1. Extension queries `{endpoint}/v1/models`
2. Parses the JSON response for available models
3. Populates the Model dropdown automatically
4. You just select and go!

**Refresh Button**: Manually reload models anytime (useful if you load a new model)

3. **Database Configuration** (Optional):
   - By default, the database is stored in:
     - Windows: `%USERPROFILE%\.burp-cookie-db\cookies.db`
     - Linux/Mac: `~/.burp-cookie-db/cookies.db`
   - You can change the location in the AI Settings tab
   - Use a shared database location for team environments

4. **Enable Auto-Processing**:
   - Check "Automatically analyze cookies from all requests"
   - Extension will passively analyze cookies as you browse
   - Results are cached in the database for instant retrieval

### Settings Overview

**Database Configuration:**
- Database Location: Path to SQLite database file
- Browse: Select custom location

**Auto-Processing:**
- Enable/disable automatic cookie discovery
- Worker Threads: Number of concurrent AI queries (1-10)
- AI Queries per Minute: Rate limit to prevent API throttling (1-60)

**AI Provider (OpenAI):**
- API Key: Your OpenAI API key
- Model: GPT model to use (gpt-4-turbo, gpt-4, gpt-3.5-turbo)
- Test: Verify API connection

**Statistics:**
- Total cookies in database
- Queue size and processed count
- Cache hit rate
- Total AI queries made

## Usage

### Managing the Cookie Database

Navigate to the "Cookie Enum" tab → "Database Viewer" sub-tab to manage stored cookie information:

**View All Cookies:**
- Browse all cookies stored in the database
- Sortable table with vendor, category, purpose, privacy impact
- Shows confidence scores and source (AI/manual/pattern)

**Edit Cookie Information:**
1. Select a cookie in the database viewer table
2. Click "Edit Selected" button
3. Modify fields:
   - Vendor name
   - Category (Essential, Analytics, Advertising, etc.)
   - Purpose description
   - Privacy impact level
   - Third-party status
   - Typical expiration
   - Confidence score
4. Click "Save" to update the database

**Delete Cookie Entries:**
1. Select one or more cookies in the table
2. Click "Delete Selected" button
3. Confirm deletion
4. Cookie information is permanently removed from database

**Use Cases:**
- Correct AI misclassifications
- Add custom cookie definitions
- Remove outdated or incorrect entries
- Maintain accurate privacy assessments

### Viewing Cookie Information

The extension adds a "Cookie Info" tab to the Inspector panel next to each request/response:

1. **Select any request** in Proxy History, Repeater, or other tools
2. **Look for the "Cookie Info" tab** in the Inspector panel (alongside Request, Response tabs)
3. **View cookie details** including:
   - **Vendor**: Company/service that set the cookie (e.g., "Google", "Facebook", "Cloudflare")
   - **Category**: Cookie type (Essential, Analytics, Advertising, Security, etc.)
   - **Purpose**: Detailed description of what the cookie does
   - **Privacy Impact**: LOW, MEDIUM, HIGH, or CRITICAL
   - **Third-party**: Whether the cookie is first-party or third-party
   - **Expiration**: Typical cookie lifetime
   - **Confidence**: AI confidence score (0-100%)

**Example Cookie Info Display:**
```
_ga
Vendor: Google Analytics
Category: Analytics & Tracking
Purpose: Used to distinguish users and sessions for analytics tracking
Privacy Impact: MEDIUM | Third-party
Expires: 2 years
Confidence: 95%
```

**Cache Performance:**
- First time seeing a cookie: Queries AI (2-5 seconds)
- Subsequent lookups: Instant from database
- Status line shows: "3 cookie(s) | 2 cached | 1 AI queries"

### Cookie Enumeration (Finding Required Cookies)

### Step 1: Navigate to a Request
In Burp Suite, find a request that contains multiple cookies. This could be in:
- Proxy history
- Repeater
- Site map
- Any other Burp tool

### Step 2: Choose Analysis Mode
Go to the "Cookie Enum" tab and choose your analysis mode:

- **Standard Analysis** (unchecked): Fast analysis, tests each cookie individually
  - Best for: Quick results, simple dependencies
  - Requests: N+1 (where N = number of cookies)

- **Deep Analysis Mode** (checked): Thorough analysis, finds minimal sets and OR relationships
  - Best for: Complex dependencies, accurate minimal sets, OR relationships
  - Requests: 2N to N² (more thorough but slower)
  - See [DEEP_ANALYSIS.md](DEEP_ANALYSIS.md) for technical details

### Step 3: Analyze Cookies
1. Right-click on the request
2. Select "Analyze Required Cookies" from the context menu
3. The extension will analyze based on the selected mode

### Step 4: View Results
Results appear in the "Cookie Enum" tab:

#### Results Table
- **Cookie Name**: Name of the cookie being tested (click column header to sort)
- **Status**: Whether the test succeeded
- **Required**: YES/NO indicating if the cookie is necessary
- **Response Code**: HTTP status code received
- **Details**: Explanation of why the cookie is/isn't required

**Sortable Columns:**
- Click any column header to sort by that column
- Click again to reverse sort order
- Useful for grouping required/optional cookies

**Multi-Select & Copy:**
- Click on any row to select a cookie
- Hold **Ctrl** (Windows/Linux) or **Cmd** (Mac) and click to select multiple cookies
- Hold **Shift** and click to select a range of cookies
- Press **Ctrl+C** (or **Cmd+C** on Mac) to copy selected cookie names to clipboard
- Cookie names are copied one per line for easy pasting

**View Request/Response:**
- Click any row to view the actual HTTP request and response
- Request viewer appears at bottom-left
- Response viewer appears at bottom-right
- Click "BASELINE" row to see the original request with all cookies
- Click "MINIMAL SET" row to see the final verification request

**MINIMAL SET Row:**
- Final row in the table after analysis completes
- Shows the verification request with ONLY the required cookies
- Proves that the identified cookies are sufficient
- Status should show "Successful" if analysis was correct

#### Status Log Window
Click "View Status Log" button to open a separate window showing:
- Request URL being analyzed
- Number of cookies found
- Individual test results
- Status codes and comparisons
- Detailed phase-by-phase progress
- Clipboard copy confirmations

### Step 5: Interpret Results

#### Standard Analysis
**Cookie is REQUIRED if:**
- Status code changes to 401 (Unauthorized) or 403 (Forbidden)
- Status code changes to 302/307 (Redirect, possibly to login)
- Status code changes to 500 (Server error)
- Response content length changes by more than 20%

**Cookie is OPTIONAL if:**
- Status code remains the same
- Response length is similar
- Overall response structure is unchanged

#### Deep Analysis
**Result Types:**
- **REQUIRED (YES)**: Cookie must be present in the minimal set
- **OPTIONAL (NO)**: Cookie is not needed at all
- **ALTERNATIVE (ALT)**: Cookie can substitute for a required cookie (OR relationship)

**Special Cases:**
- **OR Relationships**: "Required (OR alternatives: cookie2)" means either this cookie OR cookie2 must be present
- **Minimal Set**: The smallest combination of cookies that makes the request succeed
- **Alternatives**: Cookies that can replace required ones (detected in Phase 6)

**Example Deep Analysis Output:**
```
sessionId1: YES (OR alternatives: sessionId2)
sessionId2: ALT (can substitute for sessionId1)
userId: YES (no alternatives)
preferences: NO (optional)

Minimal Set: userId, sessionId1
Interpretation: Need userId AND (sessionId1 OR sessionId2)
```

## Example Scenarios

### Scenario 1: Session Cookie
```
Cookie: sessionId=abc123; userId=user1; preferences=theme:dark
```

**Analysis might show:**
- `sessionId`: REQUIRED - Status 200 → 401 without it
- `userId`: REQUIRED - Status 200 → 403 without it
- `preferences`: OPTIONAL - Status unchanged, minimal difference

**Copying Required Cookies:**
1. Select the `sessionId` row
2. Hold Ctrl and click `userId` row
3. Press Ctrl+C
4. Paste anywhere to get:
   ```
   sessionId
   userId
   ```

### Scenario 2: Analytics Cookies
```
Cookie: authToken=xyz; _ga=GA1.2.123; _gid=GA1.2.456
```

**Analysis might show:**
- `authToken`: REQUIRED - Status 200 → 302 (redirect to login)
- `_ga`: OPTIONAL - No significant change
- `_gid`: OPTIONAL - No significant change

**Quick Copy All Optional Cookies:**
1. Select both `_ga` and `_gid` rows (Ctrl+Click or Shift+Click)
2. Press Ctrl+C to copy for documentation/removal lists

## How It Works

1. **Baseline Test**: Sends the original request with all cookies and records:
   - HTTP status code
   - Response content length
   - Full response

2. **Individual Cookie Tests**: For each cookie:
   - Creates a modified request without that specific cookie
   - Sends the request
   - Compares response to baseline

3. **Analysis Logic**:
   - Checks for authentication/authorization status codes (401, 403)
   - Looks for redirects that might indicate re-authentication (302, 307)
   - Detects server errors that might result from missing cookies (500)
   - Calculates content length differences
   - Determines if cookie is required based on these factors

## Cookie Categories

The extension classifies cookies into these categories:

- **Essential/Functional**: Required for basic site functionality (session IDs, auth tokens)
- **Analytics & Tracking**: User behavior tracking (_ga, _gid, _hjid)
- **Advertising & Marketing**: Ad targeting, conversion tracking (_fbp, IDE, NID)
- **Security**: CSRF tokens, fraud detection (cf_clearance, __Secure-*)
- **Personalization**: User preferences, language settings
- **Performance**: CDN, caching, load balancing
- **Social Media**: Social sharing, login widgets
- **Unknown**: Unrecognized cookies

## Privacy Impact Levels

- **LOW**: Minimal privacy concerns (first-party functional cookies)
- **MEDIUM**: Moderate tracking (first-party analytics)
- **HIGH**: Significant tracking (third-party analytics, advertising)
- **CRITICAL**: Invasive tracking (cross-site profiling, persistent IDs)

## Cost Considerations

### Free Options (Recommended!) 💰

**LM Studio / Ollama (Local)**
- ✅ **$0.00** - runs completely locally on your machine
- ✅ No API keys or internet required
- ✅ Unlimited queries
- ✅ Complete privacy - data never leaves your machine
- Requirements: 8-16GB RAM, modern CPU (GPU optional but faster)

### Paid Cloud Options

**OpenAI**
- GPT-4-turbo: ~$0.01 per cookie (first time only)
- GPT-3.5-turbo: ~$0.001 per cookie (10x cheaper!)
- Example: 100 unique cookies = $0.10-$1.00

**Anthropic (Claude)**
- Claude 3.5 Sonnet: ~$0.015 per cookie
- Claude 3 Haiku: ~$0.0025 per cookie (cheaper)

**Mistral AI**
- Mistral Large: ~$0.004 per cookie
- Mistral Small: ~$0.0006 per cookie

**Together AI**
- Varies by model: $0.0001-$0.001 per cookie
- 100+ open source models to choose from
- Very affordable for most models

**Groq (Fast & Affordable!)**
- Llama 3 70B: ~$0.0007 per cookie
- Mixtral 8x7B: ~$0.0003 per cookie
- ⚡ Extremely fast inference with hardware acceleration

### Cost-Saving Tips:
1. **Use local models** (LM Studio/Ollama) - completely free!
2. **Choose cheaper models**: gpt-3.5-turbo, Claude Haiku, Mistral Small
3. **Groq for speed**: Fast AND cheap
4. **Database caching**: Each cookie only queried once, then cached forever
5. **Share database**: Team shares the same cache = fewer queries
6. **Rate limiting**: Prevents accidental costs from runaway queries

## Technical Details

### Architecture

**Core Components:**
- **CookieEnumExtension**: Main extension class, handles initialization and context menu registration
- **CookieEnumTab**: UI component with sortable results table, request/response viewers, and status log window
- **IntelligentCookieAnalyzer**: Unified 6-phase intelligent analysis algorithm with SHA-256 body comparison
- **AnalysisResultRow**: Model linking table rows to HTTP request/response pairs

**AI-Powered Components:**
- **CookieDatabaseManager**: SQLite database with CRUD operations for cookie information
- **AIProvider**: Interface for multiple AI providers (OpenAI, Mistral, Groq, LM Studio, etc.)
- **CookieInfoService**: Service layer coordinating database and AI provider
- **CookieProcessingQueue**: Async queue with worker threads and rate limiting
- **CookieAutoProcessor**: HTTP handler for passive cookie discovery
- **CookieInfoEditorTab**: Custom Inspector tab showing cookie details
- **CookieInfoSettingsPanel**: Settings panel for configuration
- **CookieDatabaseViewerPanel**: UI for browsing, editing, and deleting cookie database entries
- **CookieEditDialog**: Dialog for editing cookie information

### Database Schema

The SQLite database includes:
- **cookies**: Main cookie information (name, vendor, category, purpose, etc.)
- **cookie_patterns**: Regex patterns for instant matching
- **ai_query_cache**: Cache of AI responses to prevent duplicate queries
- **user_corrections**: Manual overrides and corrections
- **settings**: Extension settings

### Build System

- **Maven 3.6+**: Primary build system
- **Maven Shade Plugin**: Creates fat JAR with all dependencies
- **Java 17**: Minimum required version

### Dependencies

- **Burp Suite Montoya API 2025.10**: Official Burp extension API
- **SQLite JDBC 3.51.0.0**: Database for cookie information storage
- **AI APIs**: OpenAI, Mistral, Anthropic, Groq, Together AI, LM Studio, Ollama (9 providers)

### API Integration

- Context Menu Integration
- HTTP Request/Response handling and storage
- Custom Editor Tabs (Cookie Info)
- Settings Panel Integration
- Project File Persistence (extensionData API)
- Request/Response Viewer Components

## Limitations

**Cookie Enumeration:**
- Analysis is based on heuristics and may not catch all edge cases
- Very similar responses might be incorrectly classified
- Session-dependent behavior might affect results
- Rate limiting or anti-automation might interfere with testing

**AI Classification:**
- Requires OpenAI API key (paid service)
- AI responses may occasionally be incorrect or uncertain
- Network connectivity required for first-time classifications
- Cannot analyze cookies with obfuscated or randomized names
- Limited to cookies with recognizable patterns or documentation

## Tips

**Cookie Enumeration:**
- Test on stable endpoints for best results
- Be aware of rate limiting on the target application
- Some applications might have security mechanisms that detect cookie manipulation
- Review the detailed logs to understand the decision-making process
- Consider testing multiple times if results seem inconsistent
- Click the MINIMAL SET row to verify the final result
- Use column sorting to group required/optional cookies
- Click any row to inspect the actual request/response

**Database Management:**
- Regularly review and edit AI classifications for accuracy
- Delete test cookies or incorrect entries
- Share database files with team members for consistent results
- Back up database file before major changes

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

This extension is provided as-is for use with Burp Suite Community Edition and Burp Suite Professional, in accordance with PortSwigger's extension licensing terms.

## Testing the Extension

A Python test server is included for validating the cookie analyzer:

```bash
# Run the test server
python3 test_server.py

# Expose it via ngrok (in another terminal)
ngrok http 5000

# Use the ngrok URL in Burp Suite to test the cookie analyzer
# The server sets 10 cookies and randomly requires 2 of them
```

The test server:
- Sets 10 cookies (`cookie_0` through `cookie_9`)
- Randomly selects 2 as required for each session
- Returns consistent responses for proper SHA-256 body comparison
- Displays which cookies are required on the test page

## Troubleshooting

### Extension Won't Load
- Ensure you're using Java 17 or higher
- Check the Burp Suite extension output tab for error messages
- Verify the JAR file isn't corrupted
- Rebuild with Maven: `mvn clean package`
- Check that SQLite JDBC is included in the JAR (Maven Shade handles this)

### Cookie Info Tab Not Appearing
- Tab only appears for requests that have cookies
- Check that cookies are in the Cookie header, not Set-Cookie
- Try reloading the extension

### AI Classification Not Working
- Go to the "Cookie Enum" tab → "AI Settings" sub-tab
- Verify OpenAI API key is entered correctly
- Click "Test" button to verify API connection
- Check Burp extension output tab for error messages
- Ensure internet connectivity
- Verify API key has available credits at https://platform.openai.com/usage
- Check rate limits (default: 10 queries/min)
- Try clicking "Save Settings" after making changes

### Database Issues
- Default location: `~/.burp-cookie-db/cookies.db` (Linux/Mac) or `%USERPROFILE%\.burp-cookie-db\cookies.db` (Windows)
- Check file permissions if database fails to create
- Try specifying a custom database path in "Cookie Enum" → "AI Settings" tab
- Database is automatically created on first run

### Slow Performance
- Go to "Cookie Enum" → "AI Settings" tab
- Reduce worker threads (default: 3)
- Lower rate limit to prevent API throttling (default: 10 queries/min)
- Disable "Automatically analyze cookies from all requests" if not needed
- Check queue size in statistics panel (click "Refresh" button)

### No Cookies Found (Enumeration)
- Ensure the selected request actually contains cookies
- Check that cookies are in the Cookie header, not Set-Cookie
- Verify the request was captured correctly

### Unexpected Results (Enumeration)
- Some applications use additional anti-CSRF tokens or headers
- Session state might change between tests
- Check the status log for detailed information about each test

## Author

Created for identifying necessary authentication and session cookies in web applications during security testing and development.
