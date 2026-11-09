# Put Gambyte on the internet: step by step (AWS free tier)

This guide takes Gambyte from your laptop to a public website. Do the steps in order; each one
says **what to click or type**, and **what you should see** when it worked.

**What you will end up with:**

- One AWS EC2 server (free-tier `t3.micro`, Ubuntu) running 4 Docker containers:
  - **web**: Caddy, which serves the React site and adds HTTPS automatically;
  - **server**: Spring Boot, with the C++ engine built inside it;
  - **postgres**: the database;
  - **redis**: sessions, matchmaking and live-game snapshots.
- A free web address like `https://gambyte.duckdns.org`.

**Time:** about 1 to 1.5 hours the first time (most of it is waiting for the first build).
**Cost:** $0 within the AWS free tier. Step 2 sets a $1 alarm so nothing surprises you.

**Files used (already in the repository):**

| File | What it does |
|---|---|
| `deploy/docker-compose.prod.yml` | starts the 4 containers |
| `deploy/Dockerfile.server` | builds the engine (C++) and the server (Java) |
| `deploy/Dockerfile.web` | builds the website and puts it in Caddy |
| `deploy/Caddyfile` | HTTPS, security headers, sends `/api` and `/ws` to the server |
| `deploy/.env.example` | template for your settings and secrets (you copy it to `.env`) |

---

## Part A - Prepare (on your laptop)

### Step 1. Make sure the latest code is on GitHub

Everything is already pushed, but check once. In a terminal in the project folder:

```bash
git status
```
```bash
git push
```

**You should see:** `nothing to commit, working tree clean`, then `Everything up-to-date`.
The server will download the code from https://github.com/NikPihu/Chess_Engine_App (it is
public, so no password is needed).

### Step 2. AWS account and a billing alarm (safety first)

1. Go to https://aws.amazon.com and sign in, or **Create an AWS Account** (a card is needed for
   verification; the free tier does not charge it).
2. Top-right corner: pick the region **Asia Pacific (Mumbai) ap-south-1**. It is closest to
   India, so moves feel faster. Use this same region for every step.
3. Set the alarm:
   - Search bar → **Billing and Cost Management** → left menu **Budgets** → **Create budget**.
   - Choose **Use a template** → **Zero spend budget** (it emails you if you spend even $0.01).
   - Enter your email → **Create budget**.

**You should see:** your budget listed with status "OK".

> On free plans created after mid-2025, AWS gives credits instead of the old "12 months free".
> Either way, a `t3.micro` with a 30 GB disk stays within the free allowance.

---

## Part B - Create the server

### Step 3. Launch the EC2 instance

1. Search bar → **EC2** → orange button **Launch instance**.
2. **Name:** `gambyte`.
3. **Application and OS Images:** **Ubuntu** → **Ubuntu Server 24.04 LTS (HVM), SSD Volume Type**
   with **Architecture: 64-bit (x86)**.
   > Must be **x86**, not Arm. The engine uses an Intel/AMD instruction (BMI2/PEXT) that ARM
   > (Graviton) CPUs do not have.
4. **Instance type:** `t3.micro` (marked "Free tier eligible").
5. **Key pair (login):**
   - **Create new key pair** → name `gambyte-key` → type **RSA** → format **.pem** → **Create**.
   - The file `gambyte-key.pem` downloads. Move it to `C:\Users\nikks\.ssh\gambyte-key.pem`
     (create the `.ssh` folder if needed). **Never share or commit this file.**
6. **Network settings** → **Edit**:
   - **Auto-assign public IP:** Enable.
   - **Firewall:** Create security group, name `gambyte-sg`, with three rules:

   | Type | Port | Source | Why |
   |---|---|---|---|
   | SSH | 22 | **My IP** | only you can log in |
   | HTTP | 80 | Anywhere (0.0.0.0/0) | website, and the certificate check |
   | HTTPS | 443 | Anywhere (0.0.0.0/0) | website over HTTPS |

   (Click **Add security group rule** for the 2nd and 3rd rows.)
7. **Configure storage:** change `8` to **30** GiB, type **gp3** (30 GB is the free maximum; Docker
   images need the space).
8. Right side → **Launch instance** → **View all instances**.

**You should see:** `gambyte` with **Instance state: Running** and **Status check: 2/2 checks
passed** (takes 1-2 minutes).

### Step 4. Give it a fixed IP address (Elastic IP)

Without this the public IP changes every time the instance restarts.

1. EC2 left menu → **Network & Security** → **Elastic IPs** → **Allocate Elastic IP address** →
   **Allocate**.
2. Select the new IP → **Actions** → **Associate Elastic IP address** → **Instance:** `gambyte` →
   **Associate**.
3. Write the IP down. This guide calls it **`YOUR_IP`** (for example `13.233.45.67`).

> An Elastic IP is free **while it is attached to a running instance**. If you ever delete the
> instance, release the IP too (Actions → Release), or it costs a little per hour.

### Step 5. Connect to the server from Windows

Open **PowerShell** (not Git Bash) and run these two commands once. They make the key private,
which SSH requires:

```bash
icacls $env:USERPROFILE\.ssh\gambyte-key.pem /inheritance:r
```
```bash
icacls $env:USERPROFILE\.ssh\gambyte-key.pem /grant:r "$($env:USERNAME):(R)"
```

Then connect (replace `YOUR_IP`):

```bash
ssh -i $env:USERPROFILE\.ssh\gambyte-key.pem ubuntu@YOUR_IP
```

Type `yes` when it asks about the fingerprint.

**You should see:** a prompt like `ubuntu@ip-172-31-...:~$`. Every command from now until Part E
is typed **in this SSH window, on the server**.

> **Alternative:** EC2 → select the instance → **Connect** → **EC2 Instance Connect** →
> **Connect** opens a terminal in the browser.

---

## Part C - Install what the server needs

### Step 6. Update Ubuntu and add 2 GB of swap

The `t3.micro` has only 1 GB of RAM. Building the engine and the Java server needs more, so we
add 2 GB of "swap" (disk used as extra memory). Run one line at a time:

```bash
sudo apt update && sudo apt upgrade -y
```
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```
```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```
```bash
free -h
```

**You should see:** a `Swap:` line showing `2.0Gi`.

If `apt upgrade` shows a pink screen about restarting services, press **Enter** to accept.

### Step 7. Install Docker and Git

```bash
sudo apt install -y docker.io docker-compose-v2 git
```
```bash
sudo usermod -aG docker ubuntu
```
```bash
exit
```

Now reconnect with the same `ssh ...` command from Step 5 (the `docker` group only applies after
logging in again). Then check:

```bash
docker --version && docker compose version
```

**You should see:** two version lines, e.g. `Docker version 27...` and `Docker Compose version
v2...`.

---

## Part D - Get the code and configure it

### Step 8. Download the code

```bash
git clone https://github.com/NikPihu/Chess_Engine_App.git
```
```bash
cd Chess_Engine_App
```

**You should see:** the clone finishing with `Resolving deltas: 100%`. `ls` shows `deploy`,
`gambyte-server`, `gambyte-web`, `chess_engine_v3` and so on.

### Step 9. Create your settings file (`deploy/.env`)

Copy the template:

```bash
cp deploy/.env.example deploy/.env
```

Generate a strong database password and **copy the output**:

```bash
openssl rand -hex 24
```

Open the file in the `nano` editor:

```bash
nano deploy/.env
```

Change these lines (use the arrow keys to move; replace `YOUR_IP` with your Elastic IP):

```
SITE_ADDRESS=:80
SITE_URL=http://YOUR_IP
COOKIE_SECURE=false
GAMBYTE_ADMIN_USERNAME=nikpihu
DATABASE_PASSWORD=<paste the password from openssl here>
```

- `SITE_ADDRESS=:80` and `COOKIE_SECURE=false` are for the **first test over plain HTTP**. Step 15
  switches to HTTPS.
- `GAMBYTE_ADMIN_USERNAME` is the account that becomes admin (you register it in Step 12).
- Leave everything else as it is for now (engine settings for a 1 GB server are already in the
  template; Razorpay, email and the AI coach stay off until you fill them in).

Save and exit nano: **Ctrl+O**, then **Enter** (save), then **Ctrl+X** (exit).

Check it (this prints the lines you changed):

```bash
grep -E "SITE_|COOKIE|ADMIN|DATABASE_PASSWORD" deploy/.env
```

> `deploy/.env` holds secrets. It is listed in `.gitignore`, so it is never uploaded to GitHub.

---

## Part E - Build and start

### Step 10. Build and start everything

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

This takes **10-20 minutes the first time** on a t3.micro. It downloads base images, compiles the
C++ engine, builds the Java server, and builds the website. Later updates are faster.

**You should see:** the build output end with lines like:

```
 ✔ Container gambyte-postgres-1  Started
 ✔ Container gambyte-redis-1     Started
 ✔ Container gambyte-server-1    Started
 ✔ Container gambyte-web-1       Started
```

> **If the build stops with `Killed` or `exit code 137`**, the machine ran out of memory. Check
> that swap is on (`free -h`) and run the same command again; it resumes where it stopped. If it
> keeps failing, temporarily change the instance type to `t3.small` (Step 18) just for the first
> build, then change it back.

### Step 11. Check that it is running

```bash
docker compose -f deploy/docker-compose.prod.yml ps
```

**You should see:** all 4 containers with status `Up` (the server shows `(healthy)` after about a
minute).

Watch the server start (press **Ctrl+C** to stop watching; the server keeps running):

```bash
docker compose -f deploy/docker-compose.prod.yml logs -f server
```

**You should see**, among the JSON log lines:
- `Engine pool: 1 x /app/engine/engine_v3`: the C++ engine was found;
- `Loaded 881 seed puzzles`: the puzzles were loaded into the database;
- `Started GambyteApplication`: the server is up.

Quick test from the server itself:

```bash
curl -s localhost/api/lobby/stats
```

**You should see:** something like `{"online":0,"playing":0,"waiting":{}}`.

### Step 12. Open the site and test a game

1. On your laptop, open `http://YOUR_IP` in the browser.
   **You should see:** the Gambyte landing page with the 3D kings.
2. **Sign up** with the username you put in `GAMBYTE_ADMIN_USERNAME` (e.g. `nikpihu`).
3. Make it admin: in the SSH window, restart the server so the admin promotion runs:

   ```bash
   docker compose -f deploy/docker-compose.prod.yml restart server
   ```

   Reload the site after about a minute: a shield icon (Moderation) appears in the top bar.
4. **Engine test:** on the dashboard, Gambyte Engine → **Play the engine**, then make a few moves.
   The engine should answer.
5. **Two-player test:** open a private/incognito window, register a second account, then click
   the same quick-pairing tile (e.g. **3+0**) in both windows. Both should land in the same game.

🎉 At this point Gambyte is on the internet (over plain HTTP).

---

## Part F - A real address with HTTPS (recommended)

Browsers mark plain-HTTP sites as "Not secure", and payments need HTTPS. Caddy gets a free
certificate automatically once you have a domain name.

### Step 13. Get a free domain name (DuckDNS)

1. Go to https://www.duckdns.org and sign in (with Google or GitHub).
2. In **sub domain** type a name, e.g. `gambyte` → **add domain**.
3. In the **current ip** box next to it, type `YOUR_IP` → **update ip**.

**You should see:** `gambyte.duckdns.org` pointing to `YOUR_IP`. Check it from your laptop:

```bash
nslookup gambyte.duckdns.org
```

The answer should show your IP. Wait a few minutes if it doesn't yet.

> **Own domain instead** (about ₹800/year): buy one (e.g. at Cloudflare or Namecheap) and add a DNS
> **A record** pointing to `YOUR_IP`.

### Step 14. Point Gambyte at the domain

On the server:

```bash
nano deploy/.env
```

Change three lines (use your real domain):

```
SITE_ADDRESS=gambyte.duckdns.org
SITE_URL=https://gambyte.duckdns.org
COOKIE_SECURE=true
```

Save (**Ctrl+O**, **Enter**, **Ctrl+X**).

### Step 15. Restart with HTTPS

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d
```

Watch Caddy get the certificate (about 10-30 seconds):

```bash
docker compose -f deploy/docker-compose.prod.yml logs -f web
```

**You should see:** `certificate obtained successfully`. Then press **Ctrl+C**.

Open `https://gambyte.duckdns.org`. **You should see** the padlock in the address bar. Log in
again (cookies from the HTTP address don't carry over) and repeat the game tests from Step 12.

> **If the certificate fails:** check that ports 80 and 443 are open in the security group (Step
> 3.6) and that `nslookup` shows the right IP. Caddy retries by itself; restart it with
> `docker compose -f deploy/docker-compose.prod.yml restart web`.

---

## Part G - Optional extras (any time later)

### Step 16. Payments with Razorpay (test mode first)

1. Sign up at https://dashboard.razorpay.com and stay in **Test Mode** (toggle at the top).
2. **Account & Settings → API Keys → Generate Test Key.** Copy the **Key Id** and **Key Secret**.
3. **Subscriptions → Plans → Create plan:**
   - "Gambyte Premium Monthly": period **monthly**, amount **₹199**;
   - "Gambyte Premium Yearly": period **yearly**, amount **₹1499**.

   Copy both plan ids (`plan_...`).
4. **Account & Settings → Webhooks → Add new webhook:**
   - **URL:** `https://gambyte.duckdns.org/api/payments/webhook`
   - **Secret:** run `openssl rand -hex 24` on the server and paste the output here.
   - **Events:** `subscription.activated`, `subscription.charged`, `subscription.cancelled`,
     `subscription.halted`, `subscription.completed`
   - **Create Webhook.**
5. On the server, edit `deploy/.env` and fill in:

   ```
   RAZORPAY_KEY_ID=rzp_test_...
   RAZORPAY_KEY_SECRET=...
   RAZORPAY_WEBHOOK_SECRET=<the same secret as in the webhook>
   RAZORPAY_PLAN_MONTHLY=plan_...
   RAZORPAY_PLAN_YEARLY=plan_...
   ```

6. Apply the change:

   ```bash
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d
   ```

7. **Test:** on the site, go to **Premium → Subscribe** and pay with one of Razorpay's
   [test cards](https://razorpay.com/docs/payments/payments/test-card-details/) or the test UPI id
   `success@razorpay`. The crown appears next to your name.

**Going live** later: complete KYC in Razorpay, switch to Live Mode, create live keys, plans and
webhook, put them in `.env`, restart.

### Step 17. Emails, and the AI coach

- **Emails (password reset, verification).** Without this they only appear in the server log.
  1. AWS → **Amazon SES** → **Create identity** → verify your email or domain.
  2. **SMTP settings → Create SMTP credentials.**
  3. Put the host (e.g. `email-smtp.ap-south-1.amazonaws.com`), username and password into
     `MAIL_HOST`, `MAIL_USERNAME` and `MAIL_PASSWORD` in `.env`, and set `MAIL_FROM`.
  4. Restart as in Step 16.6.

  New SES accounts are in "sandbox" mode (they can only email verified addresses) until you
  request production access in the SES console.
- **AI coach (Premium):** put an Anthropic API key in `ANTHROPIC_API_KEY`. Optionally set
  `COACH_MODEL=claude-haiku-5-5` (much cheaper than the default). Restart. This costs money per
  question, so keep an eye on usage.

### Step 18. When you need more power

When the site gets busy (slow pages, or the engine says "busy"):

1. EC2 → select `gambyte` → **Instance state → Stop**.
2. **Actions → Instance settings → Change instance type** → `t3.small` (2 GB, about $15/month) →
   **Apply**.
3. **Instance state → Start.** The Elastic IP stays the same.
4. In `.env` set `ENGINE_POOL_SIZE=2`, `ENGINE_HASH_MB=64`, `JAVA_OPTS=-Xmx900m -XX:+UseSerialGC`
   and `SERVER_MEM_LIMIT=1200m`, then run the Step 16.6 restart command.

---

## Part H - Everyday operations

Run these on the server, inside `~/Chess_Engine_App`. Tip: to type less, run this once:

```bash
echo "alias gb='docker compose -f ~/Chess_Engine_App/deploy/docker-compose.prod.yml --env-file ~/Chess_Engine_App/deploy/.env'" >> ~/.bashrc && source ~/.bashrc
```

After that, `gb ps`, `gb logs -f server` and so on work from anywhere.

| Task | Command |
|---|---|
| Is everything up? | `gb ps` |
| Server logs | `gb logs -f server` (Ctrl+C to stop watching) |
| Restart the server | `gb restart server` |
| Stop everything | `gb down` (data is kept) |
| Start everything | `gb up -d` |
| **Deploy a new version** | `cd ~/Chess_Engine_App && git pull && gb up -d --build` |
| Back up the database | `gb exec -T postgres pg_dump -U gambyte gambyte > ~/backup-$(date +%F).sql` |
| Restore a backup | `cat ~/backup-2026-11-10.sql \| gb exec -T postgres psql -U gambyte gambyte` |
| Disk space | `df -h /` and `docker system df` |
| Free old build layers | `docker image prune -f` |
| Make someone a moderator | Moderation page → Users → *Make moderator* |

**Updating the site after you change code on your laptop:**
1. On your laptop: commit and `git push`.
2. On the server: `cd ~/Chess_Engine_App && git pull && gb up -d --build`.

Games in progress survive the restart (they are snapshotted in Redis) and resume.

**Copy a backup to your laptop** (run in PowerShell **on your laptop**):

```bash
scp -i $env:USERPROFILE\.ssh\gambyte-key.pem ubuntu@YOUR_IP:~/backup-2026-11-10.sql .
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Browser can't reach `http://YOUR_IP` | security group, or containers not up | Check port 80 is open (Step 3.6). Run `gb ps`; all 4 should be `Up`. |
| Site loads but login fails, or you keep getting logged out | `COOKIE_SECURE=true` while using `http://` | Use `false` for HTTP and `true` only with HTTPS (Step 14). Then `gb up -d`. |
| Moves don't arrive / "connection" problems | `SITE_URL` doesn't exactly match the address in the browser | Set `SITE_URL` to exactly what you type (`http://IP` or `https://domain`), then `gb up -d`. |
| `server` keeps restarting | out of memory, or a wrong DB password | `gb logs server` shows the reason. Check swap (`free -h`) and `DATABASE_PASSWORD`. |
| Changed `DATABASE_PASSWORD` after the first run and now the DB fails | Postgres keeps the first password | Put the old password back. Or, **deleting all data**: `gb down -v` then `gb up -d`. |
| Engine games are weak/slow, log says "Java engine (weaker)" | engine binary missing | `gb logs server`, then rebuild with `gb up -d --build`. |
| Certificate errors | DNS not pointing to the server, or port 80/443 closed | Step 13 check plus Step 3.6 ports, then `gb restart web`. |
| SSH says "UNPROTECTED PRIVATE KEY FILE" | key file permissions | Repeat the two `icacls` commands in Step 5. |
| SSH times out after your Wi-Fi changed | the SSH rule allows only your old IP | EC2 → Security groups → `gambyte-sg` → Edit inbound rules → SSH source → **My IP** → Save. |

---

## Costs and cleanup

- **Free tier:** `t3.micro` + 30 GB gp3 + 1 Elastic IP **attached to a running instance**.
  The Zero-spend budget from Step 2 emails you if anything costs money.
- **Pause the site:** EC2 → Instance state → **Stop** (the disk is kept; an Elastic IP on a
  stopped instance costs a little, so release it if you stop for long).
- **Remove everything:**
  1. Terminate the instance.
  2. Release the Elastic IP.
  3. In **EC2 → Volumes**, check that no volume is left.

---

### Summary checklist

- [ ] 1 Code pushed to GitHub
- [ ] 2 AWS account + zero-spend budget
- [ ] 3 EC2 `t3.micro` Ubuntu 24.04 **x86**, 30 GB, ports 22/80/443
- [ ] 4 Elastic IP attached
- [ ] 5 SSH works
- [ ] 6 Swap 2 GB
- [ ] 7 Docker installed, re-logged in
- [ ] 8 `git clone`
- [ ] 9 `deploy/.env` filled (IP, password, admin)
- [ ] 10 `up -d --build` finished
- [ ] 11 4 containers `Up`, "Started GambyteApplication" in the logs
- [ ] 12 Site opens on `http://YOUR_IP`; engine game and two-player game work
- [ ] 13-15 DuckDNS domain + HTTPS padlock
- [ ] 16-18 Razorpay, email, AI coach, upgrade (optional)
