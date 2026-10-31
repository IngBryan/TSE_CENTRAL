// Load test of the frontoffice queries (SLO 3a of section 4.3: p95 under 500 ms). Requests the pages of the
// citizens' and officials' frontoffices at a constant rate; they are rendered on the server, so each one
// includes its queries to the backend. Run it together with the tracking generator at the estimated volume.
// Configuration and commands: README.md.
import http from "k6/http";
import { check, fail } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://env-6537388.web.elasticloud.uy").replace(/\/$/, "");
const ORIGIN = BASE_URL.match(/^https?:\/\/[^/]+/)[0];
const CEDULA = __ENV.CEDULA;
const DURATION = __ENV.DURATION || "15m";
const CITIZENS_RATE = Number(__ENV.CITIZENS_RATE || 10);
const OFFICIALS_RATE = Number(__ENV.OFFICIALS_RATE || 5);

const CITIZENS = "/ciudadanos";
const OFFICIALS = "/funcionarios";
/** Detail pages are picked among at most this many ids found in the lists. */
const MAX_IDS = 50;

function scenario(rate, exec) {
  return {
    executor: "constant-arrival-rate",
    rate,
    timeUnit: "1s",
    duration: DURATION,
    preAllocatedVUs: Math.max(5, rate * 2),
    maxVUs: Math.max(20, rate * 10),
    exec,
  };
}

export const options = {
  scenarios: {
    citizens: scenario(CITIZENS_RATE, "citizens"),
    officials: scenario(OFFICIALS_RATE, "officials"),
  },
  thresholds: {
    "http_req_duration{kind:frontoffice}": ["p(95)<500"],
    "http_req_duration{front:citizens}": ["p(95)<500"],
    "http_req_duration{front:officials}": ["p(95)<500"],
    "checks{kind:frontoffice}": ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

/** Opens the sessions once for the whole test and finds the ids of the detail pages. */
export function setup() {
  if (!CEDULA) {
    fail("Missing CEDULA (-e CEDULA=...): the testing login of both frontoffices uses it");
  }
  const citizens = testingLogin(CITIZENS);
  const officials = testingLogin(OFFICIALS);
  return {
    citizens: {
      cookies: citizens,
      trips: findIds(citizens, CITIZENS, "/empresa/guias"),
      vehicles: findIds(citizens, CITIZENS, "/empresa/vehiculos"),
    },
    officials: {
      cookies: officials,
      cases: findIds(officials, OFFICIALS, "/casos"),
      companies: findIds(officials, OFFICIALS, "/empresas"),
    },
  };
}

export function citizens(data) {
  const { cookies, trips, vehicles } = data.citizens;
  query("citizens", CITIZENS, cookies, pick([
    ["/mi-cuenta"],
    ["/empresa"],
    ["/empresa/guias"],
    ["/empresa/guias/:id", trips],
    ["/empresa/vehiculos"],
    ["/empresa/vehiculos/:id", vehicles],
    ["/empresa/choferes"],
  ]));
}

export function officials(data) {
  const { cookies, cases, companies } = data.officials;
  // Without the home page: with a session it redirects to /casos
  query("officials", OFFICIALS, cookies, pick([
    ["/casos"],
    ["/casos/:id", cases],
    ["/empresas"],
    ["/empresas/:id", companies],
  ]));
}

/** Requests a page; a redirect means the session was lost, so redirects are not followed and fail the check. */
function query(front, basePath, cookies, [page, ids]) {
  const id = ids ? pick(ids) : "";
  if (ids && !id) {
    return; // no detail found in its list
  }
  const tags = { kind: "frontoffice", front, name: basePath + page };
  const res = http.get(BASE_URL + basePath + page.replace(":id", id), { cookies, redirects: 0, tags });
  check(res, { [`${basePath}${page} 200`]: (r) => r.status === 200 }, { kind: "frontoffice", front });
}

/** Testing login of a frontoffice (LOGIN_TESTING=true): the session cookie it sets. */
function testingLogin(basePath) {
  const res = http.post(`${BASE_URL}${basePath}/auth/testing`, { cedula: CEDULA }, {
    // The fronts only accept the login from their own origin (CSRF)
    headers: { Origin: ORIGIN },
    tags: { kind: "login", name: `${basePath}/auth/testing` },
  });
  if (res.status !== 200 || res.url.includes("?error=")) {
    fail(`Testing login at ${basePath} failed: ${res.status} ${res.url}`);
  }
  return cookiesFor(`${BASE_URL}${basePath}/`);
}

function cookiesFor(url) {
  const cookies = {};
  for (const [name, values] of Object.entries(http.cookieJar().cookiesForURL(url))) {
    cookies[name] = values[0];
  }
  return cookies;
}

/** Ids of the detail pages linked from a list page (href="<basePath><listPath>/<id>"). */
function findIds(cookies, basePath, listPath) {
  const res = http.get(BASE_URL + basePath + listPath, { cookies, tags: { kind: "discovery" } });
  const pattern = new RegExp(`href="${basePath}${listPath}/([^"/?#]+)"`, "g");
  const body = String(res.body);
  const ids = new Set();
  let match;
  while ((match = pattern.exec(body)) !== null) {
    if (match[1] !== "nueva") {
      ids.add(match[1]);
    }
  }
  // Without ids its detail page is skipped: in the citizens' front, the CEDULA is not a company manager
  console.log(`${basePath}${listPath}: status ${res.status}, ${ids.size} ids for the detail page`);
  return [...ids].slice(0, MAX_IDS);
}

function pick(items) {
  return items[Math.floor(Math.random() * items.length)];
}
