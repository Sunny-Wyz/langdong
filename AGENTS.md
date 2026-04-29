<claude-mem-context>
# Memory Context

# [langdong] recent context, 2026-04-29 5:30pm GMT+8

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 10 obs (4,519t read) | 0t work

### Apr 29, 2026
298 3:24p 🔵 Fresh Spring Boot Instance Started on Port 18080 with Default Security Password
299 3:26p 🔵 Valid JWT Token Obtained but All Endpoints Return 403 — Security Filter Blocking Before Routing
300 3:27p 🔵 WeeklyForecastController Is Registered and Component-Scanned — 403 Must Come from SecurityConfig Authorization Rules
301 3:28p 🔵 Temporary Port 18080 Backend Stopped to Restart with Debug Logging
302 3:29p 🔵 WeeklyForecastController Routes Registered; 403 Persists — SecurityConfig Authorization Rule Blocks All Authenticated Requests
303 3:30p 🔵 Root Cause Found: WeeklyForecastController Authorized Successfully but ai_weekly_forecast Table Has Missing Column (MySQL Error 1054)
304 3:31p 🔵 ai_weekly_forecast Table Missing No Columns — MySQL Error 1054 Is on spare_part.name Column Reference
305 3:52p 🔵 Weekly Model Training Endpoint Failure at /api/v1/weekly/train
306 " 🔵 Weekly Train Endpoint Call Chain Identified in Codebase
307 3:53p 🔵 Weekly Train Endpoint Architecture and Failure Root Cause Identified
</claude-mem-context>