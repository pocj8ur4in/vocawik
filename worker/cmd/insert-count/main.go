package main

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"html"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	pq "github.com/lib/pq"
	"github.com/redis/go-redis/v9"
	"golang.org/x/net/proxy"
)

const (
	SeedMaxConcurrentRequests = 10
	SeedMaxConcurrentPVs      = 10
	SeedMaxConcurrentYouTube  = 3
	TaskBatchSize         = 10000
	RefreshBatchSize      = 1000
	MinViewCount          = 100000
	MaxHTTPResponseSize   = 1 * 1024 * 1024
	HTTPClientTimeout     = 30 * time.Second
	BatchSize             = 30
	GCInterval            = 3
	DBBatchSize           = 10
	MaxRetries            = 10
	BaseRetryDelay        = 1 * time.Second
	TorBootstrapTimeout   = 5 * time.Minute
	TorBootstrapPollDelay = 5 * time.Second
	SeedYouTubeMinInterval    = 2 * time.Second
	RefreshMaxConcurrentRequests = 10
	RefreshMaxConcurrentPVs      = 10
	RefreshMaxConcurrentYouTube  = 2
	RefreshYouTubeMinInterval    = 2 * time.Second
	MaxTaskRetries        = 100
)

var (
	// Shared HTTP client with connection pooling to reduce memory usage
	httpClient *http.Client
	// Internal backend API calls must bypass Tor/private-address restrictions.
	backendHTTPClient *http.Client

	redisClient *redis.Client

	youtubeViewPatterns = []*regexp.Regexp{
		regexp.MustCompile(`"viewCount":"(\d+)"`),
		regexp.MustCompile(`"viewCount"\s*:\s*"(\d+)"`),
		regexp.MustCompile(`viewCount":\s*"(\d+)"`),
		regexp.MustCompile(`"videoViewCountRenderer":\s*{\s*"viewCount":\s*{\s*"simpleText":\s*"조회수\s*([\d,]+)회"`),
		regexp.MustCompile(`"videoViewCountRenderer":\s*{\s*"viewCount":\s*{\s*"simpleText":\s*"([\d,]+)\s*views"`),
		regexp.MustCompile(`<meta\s+itemprop="interactionCount"\s+content="(\d+)"`),
	}
	nicoNicoViewPatterns = []*regexp.Regexp{
		regexp.MustCompile(`"count":\{"view":(\d+)`),
		regexp.MustCompile(`"count"\s*:\s*\{\s*"view"\s*:\s*(\d+)`),
		regexp.MustCompile(`"view"\s*:\s*(\d+)\s*,\s*"comment"`),
	}
	piaproViewPattern    = regexp.MustCompile(`閲覧数：([\d,]+)`)
	soundCloudPattern    = regexp.MustCompile(`<meta property="soundcloud:play_count" content="(\d+)">`)
	nicoNicoVideoIDRegex = regexp.MustCompile(`(?:sm|nm|so)\d+`)
	youtubeVideoIDRegex  = regexp.MustCompile(`(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/v/)([^&?/]+)`)
	bilibiliAVRegex      = regexp.MustCompile(`/(av[0-9]+)`)
	bilibiliBVRegex      = regexp.MustCompile(`/(BV[0-9A-Za-z]+)`)
	torBootstrapProgress = regexp.MustCompile(`PROGRESS=(\d+)`)

	youtubeRequestMu     sync.Mutex
	youtubeNextRequestAt time.Time
	youtubeMinInterval   time.Duration
)

var errDraftSongTypeNotAllowed = fmt.Errorf("draft song type is not allowed")

type latestSongPVView struct {
	id        int64
	date      time.Time
	viewCount int
}

func dateOnlyUTC(t time.Time) time.Time {
	utc := t.UTC()
	return time.Date(utc.Year(), utc.Month(), utc.Day(), 0, 0, 0, 0, time.UTC)
}

func timestampForUTCDate(day time.Time) time.Time {
	date := dateOnlyUTC(day)
	return time.Date(date.Year(), date.Month(), date.Day(), 0, 0, 0, 0, time.UTC)
}

func interpolatedBackfillViewCount(startViews, endViews, step, totalSteps int) int {
	if totalSteps <= 0 {
		return endViews
	}
	if endViews <= startViews {
		return endViews
	}
	delta := endViews - startViews
	return startViews + (delta*step)/totalSteps
}

func medianInt(values []int) int {
	if len(values) == 0 {
		return 0
	}
	sorted := append([]int(nil), values...)
	sort.Ints(sorted)
	mid := len(sorted) / 2
	if len(sorted)%2 == 1 {
		return sorted[mid]
	}
	return (sorted[mid-1] + sorted[mid]) / 2
}

func averageInt(values []int) int {
	if len(values) == 0 {
		return 0
	}
	sum := 0
	for _, value := range values {
		sum += value
	}
	return sum / len(values)
}

func parseHistoryHintDate(value string) (time.Time, bool) {
	if strings.TrimSpace(value) == "" {
		return time.Time{}, false
	}
	parsed, err := time.Parse("2006-01-02", strings.TrimSpace(value))
	if err != nil {
		return time.Time{}, false
	}
	return dateOnlyUTC(parsed), true
}

func preferredDailyRiseHint(hint *SongPVHistoryHint) int {
	if hint == nil {
		return 0
	}
	for _, value := range []int{
		hint.Recent7dMedianRise,
		hint.Recent30dMedianRise,
		hint.Recent30dAvgRise,
	} {
		if value > 0 {
			return value
		}
	}
	return 0
}

func trendAwareBackfillViewCount(startViews, endViews, step, totalSteps int, hint *SongPVHistoryHint) int {
	linear := interpolatedBackfillViewCount(startViews, endViews, step, totalSteps)
	if hint == nil || endViews <= startViews {
		return linear
	}

	dailyRise := preferredDailyRiseHint(hint)
	if dailyRise <= 0 {
		return linear
	}

	trendValue := startViews + (dailyRise * step)
	if trendValue < startViews {
		trendValue = startViews
	}
	if trendValue > endViews {
		trendValue = endViews
	}
	if trendValue > linear {
		return linear
	}
	return trendValue
}

func upsertSongPVViewForDateTx(
	tx *sql.Tx,
	songPvID int64,
	day time.Time,
	views int,
	isFailed bool,
) error {
	day = dateOnlyUTC(day)
	timestamp := timestampForUTCDate(day)

	var existingID int64
	err := tx.QueryRow(`
		SELECT id
		FROM song_pv_views
		WHERE song_pv_id = $1
		  AND created_at::date = $2
		ORDER BY created_at DESC, id DESC
		LIMIT 1
	`, songPvID, day).Scan(&existingID)
	if err != nil && err != sql.ErrNoRows {
		return err
	}

	if err == nil {
		_, updateErr := tx.Exec(`
			UPDATE song_pv_views
			SET view_count = $2,
			    is_failed = $3,
			    updated_at = CURRENT_TIMESTAMP
			WHERE id = $1
		`, existingID, views, isFailed)
		return updateErr
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}

	_, err = tx.Exec(`
		INSERT INTO song_pv_views (
			uuid,
			created_at,
			updated_at,
			song_pv_id,
			view_count,
			is_failed
		) VALUES ($1, $2, $2, $3, $4, $5)
	`, uuidValue, timestamp, songPvID, views, isFailed)
	return err
}

func init() {
	// Initialize HTTP client with optional Tor proxy
	transport := &http.Transport{
		MaxIdleConns:        50,
		MaxIdleConnsPerHost: 10,
		IdleConnTimeout:     60 * time.Second,
		DisableKeepAlives:   false,
	}

	// Check if Tor proxy is configured
	torProxy := os.Getenv("TOR_SOCKS_PROXY")
	if torProxy != "" {
		log.Printf("Configuring HTTP client to use Tor proxy: %s", torProxy)
		proxyURL, err := url.Parse(torProxy)
		if err != nil {
			log.Fatalf("Failed to parse TOR_SOCKS_PROXY: %v", err)
		}

		dialer, err := proxy.FromURL(proxyURL, proxy.Direct)
		if err != nil {
			log.Fatalf("Failed to create proxy dialer: %v", err)
		}

		transport.Dial = dialer.Dial
	}

	httpClient = &http.Client{
		Timeout:   HTTPClientTimeout,
		Transport: transport,
	}

	backendHTTPClient = &http.Client{
		Timeout: HTTPClientTimeout,
		Transport: &http.Transport{
			MaxIdleConns:        20,
			MaxIdleConnsPerHost: 10,
			IdleConnTimeout:     60 * time.Second,
			DisableKeepAlives:   false,
		},
	}
}

type MappedSongPv struct {
	SongPvID int64
	Service  string
	URL      string
}

type DumpPv struct {
	URL string `json:"url"`
}

type DumpSongRow struct {
	ID                    int
	DefaultName           sql.NullString
	DefaultNameLanguage   sql.NullString
	MainPictureThumb      sql.NullString
	MainPictureOriginal   sql.NullString
	PublishDate           sql.NullString
	SongType              sql.NullString
	ArtistsJSON           sql.NullString
	PvsJSON               sql.NullString
	WebLinksJSON          sql.NullString
	OriginalVersionJSON   sql.NullString
	AlternateVersionsJSON sql.NullString
}

type DumpArtistRow struct {
	ID              int
	Name            sql.NullString
	AdditionalNames sql.NullString
	PicturesJSON    sql.NullString
	GroupsJSON      sql.NullString
	MembersJSON     sql.NullString
	ManagersJSON    sql.NullString
	WebLinksJSON    sql.NullString
}

type DumpSongArtistRef struct {
	ArtistID   int
	Name       string
	ArtistType string
	Roles      []string
	SortOrder  int
}

type DumpSongPV struct {
	Service     string             `json:"service"`
	URL         string             `json:"url"`
	Title       string             `json:"title"`
	HistoryHint *SongPVHistoryHint `json:"historyHint,omitempty"`
}

type DumpWebLink struct {
	URL         string `json:"url"`
	Description string `json:"description"`
	Disabled    bool   `json:"disabled"`
}

type NormalizedSongLink struct {
	LinkType    string
	Description string
	URL         string
	IsDeleted   bool
}

type NormalizedResourceLink struct {
	LinkType    string
	Description string
	URL         string
	IsDeleted   bool
}

type QueuedSongTask struct {
	Raw      string
	VocadbID int
	PVs      []DumpSongPV
	Retry    int
}

type DumpSongVersionRef struct {
	ID int `json:"id"`
}

type DumpArtistRelationRef struct {
	ArtistID   int
	Name       string
	ArtistType string
	SortOrder  int
}

type SongPvResolveExtra struct {
	AudioURL    string `json:"audioUrl"`
	CID         *int64 `json:"cid"`
	ExternalURL string `json:"externalUrl"`
}

type SongPvResolveResponse struct {
	Service         string              `json:"service"`
	VideoKey        string              `json:"videoKey"`
	Title           string              `json:"title"`
	ThumbnailURL    string              `json:"thumbnailUrl"`
	UploaderKey     string              `json:"uploaderKey"`
	DurationSeconds *int                `json:"durationSeconds"`
	PublishedAt     string              `json:"publishedAt"`
	IsDuplicated    bool                `json:"isDuplicated"`
	Extra           *SongPvResolveExtra `json:"extra"`
}

type ytDlpVideoInfo struct {
	ViewCount *int `json:"view_count"`
}

type ServiceViewInfo struct {
	SongPvID int64  `json:"songPvId"`
	Service  string `json:"service"`
	URL      string `json:"url"`
	Views    int    `json:"views"`
}

type SongPVHistoryHint struct {
	LastSuccessDate     string `json:"lastSuccessDate,omitempty"`
	LastSuccessViews    int    `json:"lastSuccessViews,omitempty"`
	Recent7dMedianRise  int    `json:"recent7dMedianRise,omitempty"`
	Recent30dMedianRise int    `json:"recent30dMedianRise,omitempty"`
	Recent30dAvgRise    int    `json:"recent30dAvgRise,omitempty"`
}

var vocalistTypes = []string{
	"SynthesizerV",
	"NEUTRINO",
	"NewType",
	"Vocaloid",
	"UTAU",
	"ACEVirtualSinger",
	"AIVOICE",
	"VOICEVOX",
	"Unknown",
	"Voiceroid",
	"CeVIO",
	"VoiSona",
	"OtherVoiceSynthesizer",
}

const (
	advisoryLockSong   = 1001
	advisoryLockArtist = 1002
	advisoryLockVocal  = 1003
)

func getDBConnectionString() string {
	host := getEnv("DB_HOST", "192.168.0.193")
	port := getEnv("DB_PORT", "5432")
	user := getEnv("DB_USER", "postgres")
	password := getEnv("DB_PASSWORD", "postgresql")
	dbname := getEnv("DB_NAME", "vocawik")

	return fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		host, port, user, password, dbname)
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvDuration(key string, defaultValue time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return defaultValue
	}
	if parsed, err := time.ParseDuration(value); err == nil {
		return parsed
	}
	if seconds, err := strconv.Atoi(value); err == nil {
		return time.Duration(seconds) * time.Second
	}
	return defaultValue
}

func isDescOrder() bool {
	return strings.EqualFold(strings.TrimSpace(getEnv("INSERT_COUNT_ORDER", "asc")), "desc")
}

func getOrderConfig() (string, string) {
	if isDescOrder() {
		return "DESC", "<"
	}
	return "ASC", ">"
}

func getMaxVocadbID(db *sql.DB) (int, error) {
	var maxID sql.NullInt64
	err := db.QueryRow(`
		SELECT MAX(CAST(substring(url from '([0-9]+)$') AS INTEGER))
		FROM song_links
		WHERE song_link_type = 'VOCADB'
		  AND is_deleted = false
		  AND substring(url from '([0-9]+)$') IS NOT NULL
	`).Scan(&maxID)
	if err != nil {
		return 0, err
	}
	if !maxID.Valid {
		return 0, nil
	}
	return int(maxID.Int64), nil
}

func getBackendBaseURL() string {
	return strings.TrimRight(getEnv("BACKEND_BASE_URL", "http://vocawik-backend.vocawik.svc.cluster.local:8888/api/v1"), "/")
}

// initRedis initializes Redis client
func initRedis() {
	redisAddr := getEnv("REDIS_ADDR", "redis.redis.svc.cluster.local:6379")
	redisPassword := getEnv("REDIS_PASSWORD", "redis")

	redisClient = redis.NewClient(&redis.Options{
		Addr:     redisAddr,
		Password: redisPassword,
		DB:       0,
	})

	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := redisClient.Ping(ctx).Err(); err != nil {
		log.Fatalf("Failed to connect to Redis: %v", err)
	}

	log.Printf("✓ Connected to Redis at %s", redisAddr)
}

func utcDateString() string {
	return time.Now().UTC().Format("2006-01-02")
}

func getSeedRedisKey() string {
	return "insert_views_over_count:seed"
}

func getRefreshRedisKey(date string) string {
	return fmt.Sprintf("insert_views_over_count:refresh:%s", date)
}

func getRedisCursorKey(redisKey string) string {
	return redisKey + ":cursor"
}

func parseUTCDate(value string) (time.Time, bool) {
	parsed, err := time.Parse("2006-01-02", value)
	if err != nil {
		return time.Time{}, false
	}
	return parsed.UTC(), true
}

func makeTaskKey(vocadbID int, pvURL string) string {
	return fmt.Sprintf("%d:%s", vocadbID, pvURL)
}

func makeNormalizedTaskLookupKey(vocadbID int, service, videoKey string) string {
	service = strings.ToUpper(strings.TrimSpace(service))
	videoKey = strings.TrimSpace(videoKey)
	if service == "" || videoKey == "" {
		return ""
	}
	return fmt.Sprintf("%d:%s:%s", vocadbID, service, videoKey)
}

func makeTaskLookupKey(vocadbID int, service, rawURL string) string {
	normalizedService := normalizePVServiceForDB(service)
	if normalizedService == "" {
		normalizedService = detectServiceNameFromURL(rawURL)
	}
	if normalizedService == "" {
		return ""
	}

	videoKey := extractVideoKeyForService(normalizedService, rawURL)
	if videoKey == "" {
		return ""
	}

	return makeNormalizedTaskLookupKey(vocadbID, normalizedService, videoKey)
}

func parseTaskKey(taskKey string) (int, string, error) {
	parts := strings.SplitN(taskKey, ":", 2)
	if len(parts) != 2 {
		return 0, "", fmt.Errorf("invalid task key: %s", taskKey)
	}

	vocadbID, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, "", fmt.Errorf("invalid VocaDB id in task key %q: %w", taskKey, err)
	}

	if strings.TrimSpace(parts[1]) == "" {
		return 0, "", fmt.Errorf("empty PV URL in task key: %s", taskKey)
	}

	return vocadbID, parts[1], nil
}

func encodeSongTask(vocadbID int, pvs []DumpSongPV) (string, error) {
	return encodeQueuedSongTask(QueuedSongTask{
		VocadbID: vocadbID,
		PVs:      pvs,
		Retry:    0,
	})
}

func encodeQueuedSongTask(task QueuedSongTask) (string, error) {
	payload := struct {
		VocadbID int          `json:"vocadbId"`
		PVs      []DumpSongPV `json:"pvs"`
		Retry    int          `json:"retry,omitempty"`
	}{
		VocadbID: task.VocadbID,
		PVs:      task.PVs,
		Retry:    task.Retry,
	}
	bytes, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

func parseSongTaskPayload(value string) (QueuedSongTask, error) {
	var payload struct {
		VocadbID int          `json:"vocadbId"`
		PVs      []DumpSongPV `json:"pvs"`
		Retry    int          `json:"retry,omitempty"`
	}
	if err := json.Unmarshal([]byte(value), &payload); err != nil {
		return QueuedSongTask{}, err
	}
	if payload.VocadbID <= 0 {
		return QueuedSongTask{}, fmt.Errorf("invalid vocadbId in task payload")
	}
	filteredPVs := make([]DumpSongPV, 0, len(payload.PVs))
	for _, pv := range payload.PVs {
		if strings.TrimSpace(pv.URL) == "" {
			continue
		}
		filteredPVs = append(filteredPVs, pv)
	}
	if len(filteredPVs) == 0 {
		return QueuedSongTask{}, fmt.Errorf("task payload has no PVs")
	}
	return QueuedSongTask{
		Raw:      value,
		VocadbID: payload.VocadbID,
		PVs:      filteredPVs,
		Retry:    payload.Retry,
	}, nil
}

func buildSongTaskPayload(
	vocadbID int,
	pvsJSON string,
	mappedSongPVsByTask map[string]MappedSongPv,
	historyHintsBySongPvID map[int64]SongPVHistoryHint,
) (string, bool, error) {
	if strings.TrimSpace(pvsJSON) == "" {
		return "", false, nil
	}

	pvs, err := parseDumpSongPVsFromJSON(pvsJSON)
	if err != nil {
		return "", false, fmt.Errorf("failed to unmarshal dump_vocadb_song.pvs for id %d: %w", vocadbID, err)
	}
	if len(pvs) == 0 {
		return "", false, nil
	}

	for i := range pvs {
		pvURL := strings.TrimSpace(pvs[i].URL)
		if pvURL == "" {
			continue
		}
		serviceName := firstNonBlank(normalizePVServiceForDB(pvs[i].Service), detectServiceNameFromURL(pvURL))
		lookupKey := makeTaskLookupKey(vocadbID, serviceName, pvURL)
		rawKey := makeTaskKey(vocadbID, pvURL)

		var mappedSongPv MappedSongPv
		ok := false
		if lookupKey != "" {
			mappedSongPv, ok = mappedSongPVsByTask[lookupKey]
		}
		if !ok && rawKey != "" {
			mappedSongPv, ok = mappedSongPVsByTask[rawKey]
		}
		if !ok {
			continue
		}
		hint, ok := historyHintsBySongPvID[mappedSongPv.SongPvID]
		if !ok {
			continue
		}
		hintCopy := hint
		pvs[i].HistoryHint = &hintCopy
	}

	taskValue, err := encodeSongTask(vocadbID, pvs)
	if err != nil {
		return "", false, err
	}
	return taskValue, true, nil
}

func applyHistoryHintsToPVs(
	vocadbID int,
	pvs []DumpSongPV,
	mappedSongPVsByTask map[string]MappedSongPv,
	historyHintsBySongPvID map[int64]SongPVHistoryHint,
) []DumpSongPV {
	if len(pvs) == 0 {
		return pvs
	}

	updated := make([]DumpSongPV, 0, len(pvs))
	for _, pv := range pvs {
		pvURL := strings.TrimSpace(pv.URL)
		if pvURL == "" {
			continue
		}
		serviceName := firstNonBlank(normalizePVServiceForDB(pv.Service), detectServiceNameFromURL(pvURL))
		lookupKey := makeTaskLookupKey(vocadbID, serviceName, pvURL)
		rawKey := makeTaskKey(vocadbID, pvURL)

		var mappedSongPv MappedSongPv
		ok := false
		if lookupKey != "" {
			mappedSongPv, ok = mappedSongPVsByTask[lookupKey]
		}
		if !ok && rawKey != "" {
			mappedSongPv, ok = mappedSongPVsByTask[rawKey]
		}
		if ok {
			if hint, hintOK := historyHintsBySongPvID[mappedSongPv.SongPvID]; hintOK {
				hintCopy := hint
				pv.HistoryHint = &hintCopy
			}
		}

		updated = append(updated, pv)
	}

	return updated
}

func loadNextTaskBatchFromDB(
	db *sql.DB,
	redisKey string,
	cursorKey string,
	mappedSongPVsByTask map[string]MappedSongPv,
	historyHintsBySongPvID map[int64]SongPVHistoryHint,
) (int, int, error) {
	ctx := context.Background()
	lastLoadedID := 0
	if cursorValue, err := redisClient.Get(ctx, cursorKey).Result(); err == nil && strings.TrimSpace(cursorValue) != "" {
		if parsed, parseErr := strconv.Atoi(strings.TrimSpace(cursorValue)); parseErr == nil {
			lastLoadedID = parsed
		}
	} else if err != nil && err != redis.Nil {
		return 0, 0, fmt.Errorf("failed to read Redis cursor %s: %w", cursorKey, err)
	}
	if lastLoadedID == 0 && isDescOrder() {
		maxID, err := getMaxVocadbID(db)
		if err != nil {
			return 0, 0, fmt.Errorf("failed to load max vocadb id: %w", err)
		}
		if maxID > 0 {
			lastLoadedID = maxID + 1
		}
	}

	log.Printf("Loading up to %d tasks from dump_vocadb_song after id %d into Redis key: %s", TaskBatchSize, lastLoadedID, redisKey)
	orderDir, comparator := getOrderConfig()
	rows, err := db.Query(fmt.Sprintf(`
		SELECT id, pvs
		FROM dump_vocadb_song
		WHERE COALESCE(deleted, false) = false
		  AND NOT EXISTS (
			  SELECT 1
			  FROM song_links sl
			  WHERE sl.song_link_type = 'VOCADB'
			    AND substring(sl.url from '([0-9]+)$')::integer = dump_vocadb_song.id
		  )
		  AND id %s $1
		ORDER BY id %s
		LIMIT $2
	`, comparator, orderDir), lastLoadedID, TaskBatchSize)
	if err != nil {
		return 0, 0, fmt.Errorf("failed to query dump_vocadb_song task batch: %w", err)
	}
	defer rows.Close()

	batchValues := make([]any, 0, TaskBatchSize)
	loaded := 0
	for rows.Next() {
		var vocadbID int
		var pvsJSON sql.NullString
		if err := rows.Scan(&vocadbID, &pvsJSON); err != nil {
			log.Printf("Failed to scan dump_vocadb_song row: %v", err)
			continue
		}
		if !pvsJSON.Valid {
			continue
		}

		taskValue, ok, err := buildSongTaskPayload(vocadbID, pvsJSON.String, mappedSongPVsByTask, historyHintsBySongPvID)
		if err != nil {
			log.Printf("Failed to build song task for VocaDB song id %d: %v", vocadbID, err)
			continue
		}
		if !ok {
			continue
		}

		batchValues = append(batchValues, taskValue)
		loaded++
		lastLoadedID = vocadbID
	}
	if err := rows.Err(); err != nil {
		return 0, 0, fmt.Errorf("error iterating dump_vocadb_song batch: %w", err)
	}

	if loaded == 0 {
		return 0, lastLoadedID, nil
	}

	if err := redisClient.RPush(ctx, redisKey, batchValues...).Err(); err != nil {
		return 0, 0, fmt.Errorf("failed to save task batch to Redis: %w", err)
	}
	if err := redisClient.Set(ctx, cursorKey, lastLoadedID, 2*24*time.Hour).Err(); err != nil {
		return 0, 0, fmt.Errorf("failed to save Redis cursor %s: %w", cursorKey, err)
	}
	if err := redisClient.Expire(ctx, redisKey, 2*24*time.Hour).Err(); err != nil {
		log.Printf("Failed to set Redis TTL for %s: %v", redisKey, err)
	}

	return loaded, lastLoadedID, nil
}

func loadNextRefreshBatchFromDB(
	db *sql.DB,
	redisKey string,
	cursorKey string,
	today time.Time,
	mappedSongPVsByTask map[string]MappedSongPv,
	historyHintsBySongPvID map[int64]SongPVHistoryHint,
) (int, int, error) {
	ctx := context.Background()
	dayStart := time.Date(today.Year(), today.Month(), today.Day(), 0, 0, 0, 0, today.Location())
	dayEnd := dayStart.Add(24 * time.Hour)
	lastLoadedID := 0
	if cursorValue, err := redisClient.Get(ctx, cursorKey).Result(); err == nil && strings.TrimSpace(cursorValue) != "" {
		if parsed, parseErr := strconv.Atoi(strings.TrimSpace(cursorValue)); parseErr == nil {
			lastLoadedID = parsed
		}
	} else if err != nil && err != redis.Nil {
		return 0, 0, fmt.Errorf("failed to read Redis cursor %s: %w", cursorKey, err)
	}
	if lastLoadedID == 0 && isDescOrder() {
		maxID, err := getMaxVocadbID(db)
		if err != nil {
			return 0, 0, fmt.Errorf("failed to load max vocadb id: %w", err)
		}
		if maxID > 0 {
			lastLoadedID = maxID + 1
		}
	}

	log.Printf("Loading up to %d refresh tasks after vocadb_id %d into Redis key: %s", RefreshBatchSize, lastLoadedID, redisKey)
	orderDir, comparator := getOrderConfig()
	idRows, err := db.Query(fmt.Sprintf(`
		SELECT vocadb_id
		FROM (
			SELECT CAST(substring(sl.url from '([0-9]+)$') AS INTEGER) AS vocadb_id
			FROM song_links sl
			WHERE sl.song_link_type = 'VOCADB'
			  AND sl.is_deleted = false
			  AND substring(sl.url from '([0-9]+)$') IS NOT NULL
			  AND CAST(substring(sl.url from '([0-9]+)$') AS INTEGER) %s $1
			GROUP BY vocadb_id
			ORDER BY vocadb_id %s
			LIMIT $2
		) t
	`, comparator, orderDir), lastLoadedID, RefreshBatchSize)
	if err != nil {
		return 0, 0, fmt.Errorf("failed to query refresh vocadb ids: %w", err)
	}
	defer idRows.Close()

	vocadbIDs := make([]int, 0, RefreshBatchSize)
	for idRows.Next() {
		var vocadbID int
		if err := idRows.Scan(&vocadbID); err != nil {
			log.Printf("Failed to scan refresh vocadb id: %v", err)
			continue
		}
		vocadbIDs = append(vocadbIDs, vocadbID)
		lastLoadedID = vocadbID
	}
	if err := idRows.Err(); err != nil {
		return 0, 0, fmt.Errorf("error iterating refresh vocadb ids: %w", err)
	}
	if len(vocadbIDs) == 0 {
		return 0, lastLoadedID, nil
	}

	rows, err := db.Query(`
		SELECT
			CAST(substring(sl.url from '([0-9]+)$') AS INTEGER) AS vocadb_id,
			sp.service,
			sp.url,
			sp.title,
			sp.sort_order,
			sp.id
		FROM song_links sl
		JOIN song_pvs sp ON sp.song_id = sl.song_id
		LEFT JOIN song_pv_views spv
			ON spv.song_pv_id = sp.id
			AND spv.created_at >= $1
			AND spv.created_at < $2
			AND spv.is_failed = false
		WHERE sl.song_link_type = 'VOCADB'
		  AND sl.is_deleted = false
		  AND sp.is_deleted = false
		  AND substring(sl.url from '([0-9]+)$') IS NOT NULL
		  AND spv.id IS NULL
		  AND CAST(substring(sl.url from '([0-9]+)$') AS INTEGER) = ANY($3)
		ORDER BY vocadb_id ASC, sp.sort_order ASC, sp.id ASC
	`, dayStart, dayEnd, pq.Array(vocadbIDs))
	if err != nil {
		return 0, 0, fmt.Errorf("failed to query refresh task batch: %w", err)
	}
	defer rows.Close()

	pvsByVocadb := make(map[int][]DumpSongPV)
	orderedVocadbIDs := make([]int, 0, len(vocadbIDs))
	seenVocadb := make(map[int]struct{})

	for rows.Next() {
		var vocadbID int
		var service sql.NullString
		var url sql.NullString
		var title sql.NullString
		var sortOrder sql.NullInt32
		var pvID sql.NullInt64
		if err := rows.Scan(&vocadbID, &service, &url, &title, &sortOrder, &pvID); err != nil {
			log.Printf("Failed to scan refresh row: %v", err)
			continue
		}
		if _, ok := seenVocadb[vocadbID]; !ok {
			seenVocadb[vocadbID] = struct{}{}
			orderedVocadbIDs = append(orderedVocadbIDs, vocadbID)
		}

		pv := DumpSongPV{
			Service: strings.TrimSpace(service.String),
			URL:     strings.TrimSpace(url.String),
			Title:   strings.TrimSpace(title.String),
		}
		pvsByVocadb[vocadbID] = append(pvsByVocadb[vocadbID], pv)
	}
	if err := rows.Err(); err != nil {
		return 0, 0, fmt.Errorf("error iterating refresh batch: %w", err)
	}

	batchValues := make([]any, 0, RefreshBatchSize)
	loaded := 0
	for _, vocadbID := range orderedVocadbIDs {
		pvs := pvsByVocadb[vocadbID]
		if len(pvs) == 0 {
			continue
		}
		updatedPVs := applyHistoryHintsToPVs(vocadbID, pvs, mappedSongPVsByTask, historyHintsBySongPvID)
		if len(updatedPVs) == 0 {
			continue
		}
		taskValue, err := encodeSongTask(vocadbID, updatedPVs)
		if err != nil {
			log.Printf("Failed to encode refresh song task for VocaDB song id %d: %v", vocadbID, err)
			continue
		}
		batchValues = append(batchValues, taskValue)
		loaded++
	}

	if loaded == 0 {
		if err := redisClient.Set(ctx, cursorKey, lastLoadedID, 2*24*time.Hour).Err(); err != nil {
			return 0, 0, fmt.Errorf("failed to save Redis cursor %s: %w", cursorKey, err)
		}
		return 0, lastLoadedID, nil
	}

	if err := redisClient.RPush(ctx, redisKey, batchValues...).Err(); err != nil {
		return 0, 0, fmt.Errorf("failed to save refresh batch to Redis: %w", err)
	}
	if err := redisClient.Set(ctx, cursorKey, lastLoadedID, 2*24*time.Hour).Err(); err != nil {
		return 0, 0, fmt.Errorf("failed to save Redis cursor %s: %w", cursorKey, err)
	}
	if err := redisClient.Expire(ctx, redisKey, 2*24*time.Hour).Err(); err != nil {
		log.Printf("Failed to set Redis TTL for %s: %v", redisKey, err)
	}

	return loaded, lastLoadedID, nil
}

func loadMappedSongPVsByTask(db *sql.DB) (map[string]MappedSongPv, error) {
	rows, err := db.Query(`
		SELECT DISTINCT
			CAST(substring(sl.url from '([0-9]+)$') AS INTEGER) AS vocadb_id,
			sp.id,
			sp.service,
			sp.video_key,
			sp.url
		FROM song_links sl
		JOIN song_pvs sp ON sp.song_id = sl.song_id
		WHERE sl.song_link_type = 'VOCADB'
		  AND sl.is_deleted = false
		  AND sp.is_deleted = false
		  AND substring(sl.url from '([0-9]+)$') IS NOT NULL
		ORDER BY vocadb_id ASC, sp.id ASC`)
	if err != nil {
		return nil, fmt.Errorf("failed to query song PV mappings: %w", err)
	}
	defer rows.Close()

	mapped := make(map[string]MappedSongPv)
	for rows.Next() {
		var vocadbID int
		var pv MappedSongPv
		var videoKey sql.NullString
		var rawURL sql.NullString
		if err := rows.Scan(&vocadbID, &pv.SongPvID, &pv.Service, &videoKey, &rawURL); err != nil {
			return nil, fmt.Errorf("failed to scan song PV mapping: %w", err)
		}
		pv.URL = strings.TrimSpace(rawURL.String)
		if normalizedKey := makeNormalizedTaskLookupKey(vocadbID, pv.Service, videoKey.String); normalizedKey != "" {
			mapped[normalizedKey] = pv
		}
		if rawKey := makeTaskKey(vocadbID, pv.URL); rawKey != "" {
			mapped[rawKey] = pv
		}
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate song PV mappings: %w", err)
	}

	return mapped, nil
}

func loadSongPVHistoryHints(db *sql.DB) (map[int64]SongPVHistoryHint, error) {
	hints := make(map[int64]SongPVHistoryHint)

	latestRows, err := db.Query(`
		SELECT song_pv_id, created_at::date, view_count
		FROM (
			SELECT
				song_pv_id,
				created_at,
				view_count,
				ROW_NUMBER() OVER (
					PARTITION BY song_pv_id
					ORDER BY created_at DESC, id DESC
				) AS rn
			FROM song_pv_views
			WHERE is_failed = false
		) latest
		WHERE rn = 1`)
	if err != nil {
		return nil, fmt.Errorf("failed to query latest song_pv_views history hints: %w", err)
	}
	defer latestRows.Close()

	for latestRows.Next() {
		var songPvID int64
		var day time.Time
		var views int
		if err := latestRows.Scan(&songPvID, &day, &views); err != nil {
			return nil, fmt.Errorf("failed to scan latest song_pv_views history hint: %w", err)
		}
		hints[songPvID] = SongPVHistoryHint{
			LastSuccessDate:  dateOnlyUTC(day).Format("2006-01-02"),
			LastSuccessViews: views,
		}
	}
	if err := latestRows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate latest song_pv_views history hints: %w", err)
	}

	type aggState struct {
		prevDate  time.Time
		prevViews int
		hasPrev   bool
		recent7d  []int
		recent30d []int
	}

	finalizeAgg := func(songPvID int64, agg aggState) {
		hint := hints[songPvID]
		hint.Recent7dMedianRise = medianInt(agg.recent7d)
		hint.Recent30dMedianRise = medianInt(agg.recent30d)
		hint.Recent30dAvgRise = averageInt(agg.recent30d)
		hints[songPvID] = hint
	}

	recentRows, err := db.Query(`
		SELECT song_pv_id, day, view_count
		FROM (
			SELECT
				song_pv_id,
				created_at::date AS day,
				view_count,
				ROW_NUMBER() OVER (
					PARTITION BY song_pv_id, created_at::date
					ORDER BY created_at DESC, id DESC
				) AS rn
			FROM song_pv_views
			WHERE is_failed = false
			  AND created_at >= (CURRENT_DATE - INTERVAL '30 days')
		) recent
		WHERE rn = 1
		ORDER BY song_pv_id ASC, day ASC`)
	if err != nil {
		return nil, fmt.Errorf("failed to query recent song_pv_views history hints: %w", err)
	}
	defer recentRows.Close()

	today := dateOnlyUTC(time.Now())
	sevenDayThreshold := today.AddDate(0, 0, -7)

	var currentSongPvID int64 = -1
	var currentAgg aggState
	for recentRows.Next() {
		var songPvID int64
		var day time.Time
		var views int
		if err := recentRows.Scan(&songPvID, &day, &views); err != nil {
			return nil, fmt.Errorf("failed to scan recent song_pv_views history hint: %w", err)
		}

		day = dateOnlyUTC(day)
		if currentSongPvID != songPvID {
			if currentSongPvID != -1 {
				finalizeAgg(currentSongPvID, currentAgg)
			}
			currentSongPvID = songPvID
			currentAgg = aggState{}
		}

		if currentAgg.hasPrev {
			dayDiff := int(day.Sub(currentAgg.prevDate).Hours() / 24)
			if dayDiff > 0 {
				dailyRise := (views - currentAgg.prevViews) / dayDiff
				if dailyRise < 0 {
					dailyRise = 0
				}
				for offset := 1; offset <= dayDiff; offset++ {
					syntheticDay := currentAgg.prevDate.AddDate(0, 0, offset)
					currentAgg.recent30d = append(currentAgg.recent30d, dailyRise)
					if !syntheticDay.Before(sevenDayThreshold) {
						currentAgg.recent7d = append(currentAgg.recent7d, dailyRise)
					}
				}
			}
		}

		currentAgg.prevDate = day
		currentAgg.prevViews = views
		currentAgg.hasPrev = true
	}
	if err := recentRows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate recent song_pv_views history hints: %w", err)
	}
	if currentSongPvID != -1 {
		finalizeAgg(currentSongPvID, currentAgg)
	}

	return hints, nil
}

func newUUIDString() (string, error) {
	value := make([]byte, 16)
	if _, err := rand.Read(value); err != nil {
		return "", err
	}

	value[6] = (value[6] & 0x0f) | 0x40
	value[8] = (value[8] & 0x3f) | 0x80

	encoded := hex.EncodeToString(value)
	return fmt.Sprintf(
		"%s-%s-%s-%s-%s",
		encoded[0:8],
		encoded[8:12],
		encoded[12:16],
		encoded[16:20],
		encoded[20:32],
	), nil
}

func normalizeNullableString(value sql.NullString) string {
	if !value.Valid {
		return ""
	}
	return strings.TrimSpace(value.String)
}

func firstNonBlank(values ...string) string {
	for _, value := range values {
		trimmed := strings.TrimSpace(value)
		if trimmed != "" {
			return trimmed
		}
	}
	return ""
}

func nullIfBlank(value string) sql.NullString {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: trimmed, Valid: true}
}

func mapVocadbLanguage(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "japanese":
		return "JA"
	case "english":
		return "EN"
	case "korean":
		return "KO"
	case "chinese":
		return "ZH"
	case "romaji":
		return "LA"
	default:
		return "UND"
	}
}

func mapSongType(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "original":
		return "ORIGINAL"
	case "cover":
		return "COVER"
	case "remix":
		return "REMIX"
	case "remaster":
		return "REMASTER"
	case "mashup":
		return "MASHUP"
	default:
		return "OTHER"
	}
}

func isDraftableSongType(value string) bool {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "original", "cover", "remix", "remaster", "mashup":
		return true
	default:
		return false
	}
}

func parsePublishedAt(value string) any {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return nil
	}

	layouts := []string{
		time.RFC3339,
		"2006-01-02T15:04:05",
		"2006-01-02",
	}

	for _, layout := range layouts {
		parsed, err := time.Parse(layout, trimmed)
		if err == nil {
			return parsed.UTC()
		}
	}

	return nil
}

func asMap(value any) map[string]any {
	result, ok := value.(map[string]any)
	if !ok {
		return nil
	}
	return result
}

func asArray(value any) []any {
	result, ok := value.([]any)
	if !ok {
		return nil
	}
	return result
}

func asString(value any) string {
	switch converted := value.(type) {
	case string:
		return strings.TrimSpace(converted)
	case fmt.Stringer:
		return strings.TrimSpace(converted.String())
	default:
		return ""
	}
}

func asInt(value any) int {
	switch converted := value.(type) {
	case float64:
		return int(converted)
	case float32:
		return int(converted)
	case int:
		return converted
	case int64:
		return int(converted)
	case json.Number:
		parsed, err := converted.Int64()
		if err == nil {
			return int(parsed)
		}
	}
	return 0
}

func parseDumpSongArtists(value sql.NullString) []DumpSongArtistRef {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return nil
	}

	var payload []any
	if err := json.Unmarshal([]byte(value.String), &payload); err != nil {
		return nil
	}

	artists := make([]DumpSongArtistRef, 0, len(payload))
	for index, item := range payload {
		itemMap := asMap(item)
		if itemMap == nil {
			continue
		}

		ref := DumpSongArtistRef{
			SortOrder: index,
		}

		artistMap := asMap(itemMap["artist"])
		if artistMap != nil {
			ref.ArtistID = asInt(artistMap["id"])
			ref.Name = asString(artistMap["name"])
			ref.ArtistType = asString(artistMap["artistType"])
		}

		if ref.Name == "" {
			ref.Name = firstNonBlank(asString(itemMap["name"]), asString(itemMap["artistString"]))
		}

		rolesRaw := asString(itemMap["roles"])
		if rolesRaw == "" {
			rolesRaw = asString(itemMap["effectiveRoles"])
		}
		if rolesRaw != "" {
			for _, role := range strings.Split(rolesRaw, ",") {
				trimmed := strings.TrimSpace(role)
				if trimmed != "" {
					ref.Roles = append(ref.Roles, trimmed)
				}
			}
		}

		if ref.ArtistID <= 0 && ref.Name == "" {
			continue
		}

		artists = append(artists, ref)
	}

	return artists
}

func parseDumpSongPVs(value sql.NullString) []DumpSongPV {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return nil
	}

	pvs, err := parseDumpSongPVsFromJSON(value.String)
	if err != nil {
		return nil
	}
	return pvs
}

func parseDumpSongPVsFromJSON(raw string) ([]DumpSongPV, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, nil
	}

	var payload any
	if err := json.Unmarshal([]byte(raw), &payload); err != nil {
		return nil, err
	}

	items := make([]any, 0, 1)
	switch typed := payload.(type) {
	case []any:
		items = typed
	case map[string]any:
		items = append(items, typed)
	default:
		return nil, nil
	}

	pvs := make([]DumpSongPV, 0, len(items))
	for _, item := range items {
		itemMap := asMap(item)
		if itemMap == nil {
			continue
		}

		pv := DumpSongPV{
			Service: firstNonBlank(asString(itemMap["service"]), detectServiceNameFromURL(asString(itemMap["url"]))),
			URL:     asString(itemMap["url"]),
			Title:   firstNonBlank(asString(itemMap["name"]), asString(itemMap["title"])),
		}
		if pv.URL == "" {
			continue
		}
		pvs = append(pvs, pv)
	}

	return pvs, nil
}

func parseDumpOriginalVersionID(value sql.NullString) int {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return 0
	}

	var ref DumpSongVersionRef
	if err := json.Unmarshal([]byte(value.String), &ref); err != nil {
		return 0
	}
	return ref.ID
}

func parseDumpVersionIDs(value sql.NullString) []int {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return nil
	}

	var refs []DumpSongVersionRef
	if err := json.Unmarshal([]byte(value.String), &refs); err != nil {
		return nil
	}

	result := make([]int, 0, len(refs))
	for _, ref := range refs {
		if ref.ID > 0 {
			result = append(result, ref.ID)
		}
	}
	return result
}

func parseDumpArtistRelationRefs(value sql.NullString) []DumpArtistRelationRef {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return nil
	}

	var payload []any
	if err := json.Unmarshal([]byte(value.String), &payload); err != nil {
		return nil
	}

	refs := make([]DumpArtistRelationRef, 0, len(payload))
	for index, item := range payload {
		itemMap := asMap(item)
		if itemMap == nil {
			continue
		}

		ref := DumpArtistRelationRef{
			ArtistID:   asInt(itemMap["id"]),
			Name:       firstNonBlank(asString(itemMap["name"]), asString(itemMap["defaultName"])),
			ArtistType: asString(itemMap["artistType"]),
			SortOrder:  index,
		}
		if ref.ArtistID <= 0 || !isDraftableArtistType(ref.ArtistType) {
			continue
		}
		refs = append(refs, ref)
	}

	return refs
}

func resolveSongPVMetadata(rawURL string) (*SongPvResolveResponse, error) {
	requestBody, err := json.Marshal(map[string]string{
		"url":          strings.TrimSpace(rawURL),
		"captchaToken": getEnv("BACKEND_CAPTCHA_TOKEN", ""),
	})
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest(http.MethodPost, getBackendBaseURL()+"/songs/pvs", strings.NewReader(string(requestBody)))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if internalToken := strings.TrimSpace(getEnv("BACKEND_INTERNAL_TOKEN", "")); internalToken != "" {
		req.Header.Set("X-Internal-Token", internalToken)
	}

	resp, err := backendHTTPClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, MaxHTTPResponseSize))
	if err != nil {
		return nil, err
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("backend returned status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}

	var payload SongPvResolveResponse
	if err := json.Unmarshal(body, &payload); err != nil {
		return nil, err
	}
	return &payload, nil
}

func detectServiceNameFromURL(rawURL string) string {
	normalized := strings.ToLower(strings.TrimSpace(rawURL))
	switch {
	case strings.Contains(normalized, "youtube.com") || strings.Contains(normalized, "youtu.be"):
		return "YOUTUBE"
	case strings.Contains(normalized, "nicovideo.jp") || strings.Contains(normalized, "nico.ms"):
		return "NICONICO"
	case strings.Contains(normalized, "bilibili.com"):
		return "BILIBILI"
	case strings.Contains(normalized, "piapro.jp"):
		return "PIAPRO"
	case strings.Contains(normalized, "soundcloud.com"):
		return "SOUNDCLOUD"
	default:
		return ""
	}
}

func normalizePVServiceForDB(value string) string {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case "YOUTUBE", "YOUTUBEVIDEO", "YOUTUBEPV":
		return "YOUTUBE"
	case "NICONICO", "NICONICODOUGA":
		return "NICONICO"
	case "BILIBILI":
		return "BILIBILI"
	case "PIAPRO":
		return "PIAPRO"
	case "SOUNDCLOUD":
		return "SOUNDCLOUD"
	default:
		return ""
	}
}

func extractVideoKeyForService(service string, rawURL string) string {
	switch service {
	case "YOUTUBE":
		return extractYoutubeVideoID(rawURL)
	case "NICONICO":
		return extractNicoNicoVideoID(rawURL)
	case "BILIBILI":
		if matches := bilibiliBVRegex.FindStringSubmatch(rawURL); len(matches) > 1 {
			return matches[1]
		}
		if matches := bilibiliAVRegex.FindStringSubmatch(rawURL); len(matches) > 1 {
			return matches[1]
		}
		return ""
	case "PIAPRO":
		parts := strings.Split(strings.TrimRight(rawURL, "/"), "/")
		if len(parts) == 0 {
			return ""
		}
		return parts[len(parts)-1]
	case "SOUNDCLOUD":
		normalized := strings.TrimSpace(rawURL)
		normalized = strings.TrimPrefix(normalized, "https://")
		normalized = strings.TrimPrefix(normalized, "http://")
		normalized = strings.TrimPrefix(normalized, "www.")
		normalized = strings.TrimPrefix(normalized, "soundcloud.com/")
		return normalized
	default:
		return ""
	}
}

func isVocalistType(value string) bool {
	for _, item := range vocalistTypes {
		if strings.EqualFold(item, strings.TrimSpace(value)) {
			return true
		}
	}
	return false
}

func isDraftableArtistType(value string) bool {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return false
	}
	if isVocalistType(trimmed) {
		return false
	}
	return !strings.HasPrefix(strings.ToLower(trimmed), "other")
}

func mapArtistTypeToRoles(artistType string) []string {
	normalized := strings.ToLower(strings.TrimSpace(artistType))
	switch normalized {
	case "producer", "coverartist":
		return []string{"PRODUCER"}
	default:
		return nil
	}
}

func mapArtistRoles(roles []string, artistType string) []string {
	result := make([]string, 0, len(roles))
	seen := make(map[string]struct{})
	onlyDefaultish := len(roles) > 0
	for _, role := range roles {
		mapped := ""
		normalized := strings.ToLower(strings.TrimSpace(role))
		switch normalized {
		case "producer":
			mapped = "PRODUCER"
		case "arranger":
			mapped = "ARRANGER"
		case "composer":
			mapped = "COMPOSER"
		case "lyricist", "lyrics":
			mapped = "LYRICIST"
		case "instrumentalist":
			mapped = "INSTRUMENTALIST"
		case "vocalist":
			mapped = "VOCALIST"
		case "mastering":
			mapped = "MASTERING"
		case "mixer":
			mapped = "MIXER"
		case "voice manipulator", "voice_manipulator":
			mapped = "VOICE_MANIPULATOR"
		default:
			mapped = ""
		}

		if normalized != "" && normalized != "default" {
			onlyDefaultish = false
		}
		if mapped == "" {
			continue
		}

		if _, exists := seen[mapped]; exists {
			continue
		}
		seen[mapped] = struct{}{}
		result = append(result, mapped)
	}

	if len(result) == 0 || onlyDefaultish {
		if fallback := mapArtistTypeToRoles(artistType); len(fallback) > 0 {
			return fallback
		}
	}

	return result
}

func extractThumbnailFromPictures(value sql.NullString) string {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return ""
	}

	var payload []any
	if err := json.Unmarshal([]byte(value.String), &payload); err != nil {
		return ""
	}

	for _, item := range payload {
		itemMap := asMap(item)
		if itemMap == nil {
			continue
		}

		if mainPicture := asMap(itemMap["mainPicture"]); mainPicture != nil {
			if url := firstNonBlank(asString(mainPicture["urlThumb"]), asString(mainPicture["urlOriginal"])); url != "" {
				return url
			}
		}

		if url := firstNonBlank(asString(itemMap["urlThumb"]), asString(itemMap["urlOriginal"])); url != "" {
			return url
		}
	}

	return ""
}

func acquireAdvisoryXactLock(tx *sql.Tx, namespace int, key int) error {
	_, err := tx.Exec("SELECT pg_advisory_xact_lock($1, $2)", namespace, key)
	return err
}

func getDumpSongRowTx(tx *sql.Tx, vocadbID int) (DumpSongRow, error) {
	var row DumpSongRow
	err := tx.QueryRow(`
		SELECT
			id,
			song_default_name,
			song_default_name_language,
			song_main_picture_url_thumb,
			song_main_picture_url_original,
			song_publish_date,
			song_type,
			artists,
			pvs,
			web_links,
			original_version,
			alternate_versions
		FROM dump_vocadb_song
		WHERE id = $1
		  AND COALESCE(deleted, false) = false
		LIMIT 1`, vocadbID).
		Scan(
			&row.ID,
			&row.DefaultName,
			&row.DefaultNameLanguage,
			&row.MainPictureThumb,
			&row.MainPictureOriginal,
			&row.PublishDate,
			&row.SongType,
			&row.ArtistsJSON,
			&row.PvsJSON,
			&row.WebLinksJSON,
			&row.OriginalVersionJSON,
			&row.AlternateVersionsJSON,
		)
	return row, err
}

func getDumpArtistRowTx(tx *sql.Tx, artistID int) (DumpArtistRow, error) {
	var row DumpArtistRow
	err := tx.QueryRow(`
		SELECT id, name, additional_names, pictures, groups, members, managers, web_links
		FROM dump_vocadb_artist
		WHERE id = $1
		LIMIT 1`, artistID).
		Scan(
			&row.ID,
			&row.Name,
			&row.AdditionalNames,
			&row.PicturesJSON,
			&row.GroupsJSON,
			&row.MembersJSON,
			&row.ManagersJSON,
			&row.WebLinksJSON,
		)
	return row, err
}

func findExistingArtistIDByVocadbIDTx(tx *sql.Tx, vocadbID int) (int64, error) {
	var artistID int64
	err := tx.QueryRow(`
		SELECT a.id
		FROM artists a
		JOIN artist_links al ON al.artist_id = a.id
		WHERE al.artist_link_type = 'VOCADB'
		  AND substring(al.url from '([0-9]+)$')::integer = $1
		ORDER BY al.id ASC
		LIMIT 1`, vocadbID).Scan(&artistID)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	return artistID, err
}

func findExistingVocalIDByVocadbIDTx(tx *sql.Tx, vocadbID int) (int64, error) {
	var vocalID int64
	err := tx.QueryRow(`
		SELECT v.id
		FROM vocals v
		JOIN vocal_links vl ON vl.vocal_id = v.id
		WHERE vl.vocal_link_type = 'VOCADB'
		  AND substring(vl.url from '([0-9]+)$')::integer = $1
		ORDER BY vl.id ASC
		LIMIT 1`, vocadbID).Scan(&vocalID)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	return vocalID, err
}

func findExistingSongIDByVocadbIDTx(tx *sql.Tx, vocadbID int) (int64, error) {
	var songID int64
	err := tx.QueryRow(`
		SELECT s.id
		FROM songs s
		JOIN song_links sl ON sl.song_id = s.id
		WHERE sl.song_link_type = 'VOCADB'
		  AND substring(sl.url from '([0-9]+)$')::integer = $1
		ORDER BY sl.id ASC
		LIMIT 1`, vocadbID).Scan(&songID)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	return songID, err
}

func countSongVocalsTx(tx *sql.Tx, songID int64) (int, error) {
	var count int
	err := tx.QueryRow(`SELECT COUNT(*) FROM song_vocals WHERE song_id = $1`, songID).Scan(&count)
	return count, err
}

func findChildVocadbSongIDsByOriginalVersionTx(tx *sql.Tx, originalVersionID int) ([]int, error) {
	rows, err := tx.Query(`
		SELECT id
		FROM dump_vocadb_song
		WHERE song_original_version_id = $1
		  AND COALESCE(deleted, false) = false
		ORDER BY id ASC
	`, originalVersionID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	childIDs := make([]int, 0)
	for rows.Next() {
		var childID int
		if err := rows.Scan(&childID); err != nil {
			return nil, err
		}
		childIDs = append(childIDs, childID)
	}
	return childIDs, rows.Err()
}

func ensureSongRelationTx(tx *sql.Tx, sourceSongID int64, targetSongID int64) (bool, error) {
	if sourceSongID <= 0 || targetSongID <= 0 || sourceSongID == targetSongID {
		return false, nil
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return false, err
	}

	var relationID int64
	err = tx.QueryRow(`
		INSERT INTO song_relations (
			uuid,
			created_at,
			updated_at,
			source_song_id,
			target_song_id
		) VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2, $3)
		ON CONFLICT DO NOTHING
		RETURNING id`,
		uuidValue,
		sourceSongID,
		targetSongID,
	).Scan(&relationID)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return relationID > 0, nil
}

func ensureArtistGroupTx(tx *sql.Tx, groupArtistID int64, memberArtistID int64, sortOrder int) (bool, error) {
	if groupArtistID <= 0 || memberArtistID <= 0 || groupArtistID == memberArtistID {
		return false, nil
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return false, err
	}

	var artistGroupID int64
	err = tx.QueryRow(`
		INSERT INTO artist_groups (
			uuid,
			created_at,
			updated_at,
			group_artist_id,
			member_artist_id,
			sort_order
		) VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2, $3, $4)
		ON CONFLICT DO NOTHING
		RETURNING id`,
		uuidValue,
		groupArtistID,
		memberArtistID,
		sortOrder,
	).Scan(&artistGroupID)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return artistGroupID > 0, nil
}

func buildSongHistorySnapshotTx(tx *sql.Tx, songID int64) ([]byte, error) {
	var snapshot []byte
	err := tx.QueryRow(`
		SELECT jsonb_build_object(
			'canonicalName', r.canonical_name,
			'thumbnailUrl', to_jsonb(r.thumbnail_url),
			'content', to_jsonb(s.content),
			'links', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'type', sl.song_link_type,
						'url', sl.url,
						'content', to_jsonb(sl.content),
						'isDeleted', sl.is_deleted
					)
					ORDER BY sl.id
				)
				FROM song_links sl
				WHERE sl.song_id = s.id
			), '[]'::jsonb),
			'publishedAt', to_jsonb(s.published_at::text),
			'songType', s.song_type,
			'names', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'langCode', rn.lang_code,
						'name', rn.name,
						'isPrimary', rn.is_primary,
						'sortOrder', rn.sort_order
					)
					ORDER BY rn.sort_order, rn.id
				)
				FROM resource_names rn
				WHERE rn.resource_id = r.id
			), '[]'::jsonb),
			'acls', '[]'::jsonb,
			'lyrics', '[]'::jsonb,
			'pvs', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'service', sp.service,
						'videoKey', sp.video_key,
						'url', to_jsonb(sp.url),
						'title', to_jsonb(sp.title),
						'thumbnailUrl', to_jsonb(sp.thumbnail_url),
						'uploaderKey', to_jsonb(sp.uploader_key),
						'durationSeconds', to_jsonb(sp.duration_seconds),
						'isOfficial', sp.is_official,
						'publishedAt', to_jsonb(sp.published_at::text),
						'extra', CASE
							WHEN sp.piapro_audio_url IS NULL AND sp.bilibili_cid IS NULL AND sp.bandcamp_external_url IS NULL THEN NULL
							ELSE jsonb_build_object(
								'audioUrl', to_jsonb(sp.piapro_audio_url),
								'cid', to_jsonb(sp.bilibili_cid),
								'externalUrl', to_jsonb(sp.bandcamp_external_url)
							)
						END,
						'sortOrder', sp.sort_order
					)
					ORDER BY sp.sort_order, sp.id
				)
				FROM song_pvs sp
				WHERE sp.song_id = s.id
				  AND sp.is_deleted = false
			), '[]'::jsonb),
			'artists', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'artistResourceUuid', ar.uuid::text,
						'roles', to_jsonb(ARRAY(
							SELECT role_item
							FROM unnest(sa.role) role_item
							ORDER BY role_item
						)),
						'isMain', sa.is_main,
						'sortOrder', sa.sort_order
					)
					ORDER BY sa.sort_order, sa.id
				)
				FROM song_artists sa
				JOIN resources ar ON ar.id = sa.artist_id
				WHERE sa.song_id = s.id
			), '[]'::jsonb),
			'vocals', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'vocalResourceUuid', vr.uuid::text,
						'isMain', sv.is_main,
						'sortOrder', sv.sort_order
					)
					ORDER BY sv.sort_order, sv.id
				)
				FROM song_vocals sv
				JOIN resources vr ON vr.id = sv.vocal_id
				WHERE sv.song_id = s.id
			), '[]'::jsonb),
			'relations', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'targetSongResourceUuid', tr.uuid::text
					)
					ORDER BY sr.id
				)
				FROM song_relations sr
				JOIN resources tr ON tr.id = sr.target_song_id
				WHERE sr.source_song_id = s.id
			), '[]'::jsonb)
		)::text
		FROM songs s
		JOIN resources r ON r.id = s.id
		WHERE s.id = $1
	`, songID).Scan(&snapshot)
	return snapshot, err
}

func buildArtistHistorySnapshotTx(tx *sql.Tx, artistID int64) ([]byte, error) {
	var snapshot []byte
	err := tx.QueryRow(`
		SELECT jsonb_build_object(
			'canonicalName', r.canonical_name,
			'thumbnailUrl', to_jsonb(r.thumbnail_url),
			'content', to_jsonb(a.content),
			'links', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'type', al.artist_link_type,
						'url', al.url,
						'content', to_jsonb(al.content),
						'isDeleted', al.is_deleted
					)
					ORDER BY al.id
				)
				FROM artist_links al
				WHERE al.artist_id = a.id
			), '[]'::jsonb),
			'names', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'langCode', rn.lang_code,
						'name', rn.name,
						'isPrimary', rn.is_primary,
						'sortOrder', rn.sort_order
					)
					ORDER BY rn.sort_order, rn.id
				)
				FROM resource_names rn
				WHERE rn.resource_id = r.id
			), '[]'::jsonb),
			'acls', '[]'::jsonb,
			'groups', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'memberArtistResourceUuid', mr.uuid::text,
						'sortOrder', ag.sort_order
					)
					ORDER BY ag.sort_order, ag.id
				)
				FROM artist_groups ag
				JOIN resources mr ON mr.id = ag.member_artist_id
				WHERE ag.group_artist_id = a.id
			), '[]'::jsonb),
			'members', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'groupArtistResourceUuid', gr.uuid::text,
						'sortOrder', ag.sort_order
					)
					ORDER BY ag.sort_order, ag.id
				)
				FROM artist_groups ag
				JOIN resources gr ON gr.id = ag.group_artist_id
				WHERE ag.member_artist_id = a.id
			), '[]'::jsonb)
		)::text
		FROM artists a
		JOIN resources r ON r.id = a.id
		WHERE a.id = $1
	`, artistID).Scan(&snapshot)
	return snapshot, err
}

func buildVocalHistorySnapshotTx(tx *sql.Tx, vocalID int64) ([]byte, error) {
	var snapshot []byte
	err := tx.QueryRow(`
		SELECT jsonb_build_object(
			'canonicalName', r.canonical_name,
			'thumbnailUrl', to_jsonb(r.thumbnail_url),
			'content', to_jsonb(v.content),
			'links', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'type', vl.vocal_link_type,
						'url', vl.url,
						'content', to_jsonb(vl.content),
						'isDeleted', vl.is_deleted
					)
					ORDER BY vl.id
				)
				FROM vocal_links vl
				WHERE vl.vocal_id = v.id
			), '[]'::jsonb),
			'names', COALESCE((
				SELECT jsonb_agg(
					jsonb_build_object(
						'langCode', rn.lang_code,
						'name', rn.name,
						'isPrimary', rn.is_primary,
						'sortOrder', rn.sort_order
					)
					ORDER BY rn.sort_order, rn.id
				)
				FROM resource_names rn
				WHERE rn.resource_id = r.id
			), '[]'::jsonb),
			'acls', '[]'::jsonb
		)::text
		FROM vocals v
		JOIN resources r ON r.id = v.id
		WHERE v.id = $1
	`, vocalID).Scan(&snapshot)
	return snapshot, err
}

func recordCreateHistoryTx(tx *sql.Tx, resourceID int64, snapshot []byte) error {
	if len(snapshot) == 0 {
		return fmt.Errorf("snapshot is required for history")
	}

	var revision int
	if err := tx.QueryRow(`
		UPDATE resources
		SET revision = revision + 1,
			updated_at = CURRENT_TIMESTAMP
		WHERE id = $1
		RETURNING revision
	`, resourceID).Scan(&revision); err != nil {
		return err
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}

	actorUserID, err := resolveHistoryActorUserIDTx(tx)
	if err != nil {
		return err
	}

	hash := sha256.Sum256(snapshot)
	_, err = tx.Exec(`
		INSERT INTO histories (
			uuid,
			created_at,
			updated_at,
			resource_id,
			revision,
			base_revision,
			action_type,
			actor_user_id,
			actor_guest_id,
			snapshot_data,
			content_hash
		) VALUES (
			$1,
			CURRENT_TIMESTAMP,
			CURRENT_TIMESTAMP,
			$2,
			$3,
			0,
			'CREATE',
			$4,
			NULL,
			$5::jsonb,
			$6
		)
	`, uuidValue, resourceID, revision, actorUserID, string(snapshot), hex.EncodeToString(hash[:]))
	return err
}

func recordUpdateHistoryTx(tx *sql.Tx, resourceID int64, snapshot []byte) error {
	if len(snapshot) == 0 {
		return fmt.Errorf("snapshot is required for history")
	}

	var baseRevision int
	if err := tx.QueryRow(`
		SELECT revision
		FROM resources
		WHERE id = $1
		FOR UPDATE
	`, resourceID).Scan(&baseRevision); err != nil {
		return err
	}

	var revision int
	if err := tx.QueryRow(`
		UPDATE resources
		SET revision = revision + 1,
			updated_at = CURRENT_TIMESTAMP
		WHERE id = $1
		RETURNING revision
	`, resourceID).Scan(&revision); err != nil {
		return err
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}

	actorUserID, err := resolveHistoryActorUserIDTx(tx)
	if err != nil {
		return err
	}

	hash := sha256.Sum256(snapshot)
	_, err = tx.Exec(`
		INSERT INTO histories (
			uuid,
			created_at,
			updated_at,
			resource_id,
			revision,
			base_revision,
			action_type,
			actor_user_id,
			actor_guest_id,
			snapshot_data,
			content_hash
		) VALUES (
			$1,
			CURRENT_TIMESTAMP,
			CURRENT_TIMESTAMP,
			$2,
			$3,
			$4,
			'UPDATE',
			$5,
			NULL,
			$6::jsonb,
			$7
		)
	`, uuidValue, resourceID, revision, baseRevision, actorUserID, string(snapshot), hex.EncodeToString(hash[:]))
	return err
}

func resolveHistoryActorUserIDTx(tx *sql.Tx) (int64, error) {
	var userID int64
	err := tx.QueryRow(`
		SELECT id
		FROM users
		WHERE nickname = '폭주린'
		ORDER BY id ASC
		LIMIT 1
	`).Scan(&userID)
	if err != nil {
		if err == sql.ErrNoRows {
			return 0, fmt.Errorf("history actor user not found for nickname 폭주린")
		}
		return 0, err
	}
	return userID, nil
}

func insertResourceTx(tx *sql.Tx, canonicalName string, thumbnailURL string, resourceType string) (int64, error) {
	var resourceID int64
	uuidValue, err := newUUIDString()
	if err != nil {
		return 0, err
	}

	err = tx.QueryRow(`
		INSERT INTO resources (
			uuid,
			created_at,
			updated_at,
			canonical_name,
			thumbnail_url,
			view_count,
			status,
			is_deleted,
			resource_type,
			revision
		) VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2, $3, 0, 'DRAFT', false, $4, 0)
		RETURNING id`, uuidValue, canonicalName, sql.NullString{String: thumbnailURL, Valid: thumbnailURL != ""}, resourceType).
		Scan(&resourceID)
	return resourceID, err
}

func insertPrimaryResourceNameTx(tx *sql.Tx, resourceID int64, langCode string, name string) error {
	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}

	_, err = tx.Exec(`
		INSERT INTO resource_names (
			uuid,
			created_at,
			updated_at,
			resource_id,
			lang_code,
			name,
			is_primary,
			sort_order
		) VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2, $3, $4, true, 0)`,
		uuidValue, resourceID, langCode, name)
	return err
}

func ensureVocadbArtistLinkTx(tx *sql.Tx, artistID int64, vocadbID int) error {
	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}
	_, err = tx.Exec(`
		INSERT INTO artist_links (
			uuid,
			created_at,
			updated_at,
			artist_id,
			artist_link_type,
			url,
			content,
			is_deleted
		)
		SELECT $1::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2::bigint, 'VOCADB', $3::varchar, NULL, false
		WHERE NOT EXISTS (
			SELECT 1
			FROM artist_links
			WHERE artist_id = $2::bigint
			  AND artist_link_type = 'VOCADB'
			  AND url = $3::varchar
		)`, uuidValue, artistID, fmt.Sprintf("https://vocadb.net/Ar/%d", vocadbID))
	return err
}

func ensureVocadbVocalLinkTx(tx *sql.Tx, vocalID int64, vocadbID int) error {
	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}
	_, err = tx.Exec(`
		INSERT INTO vocal_links (
			uuid,
			created_at,
			updated_at,
			vocal_id,
			vocal_link_type,
			url,
			content,
			is_deleted
		)
		SELECT $1::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2::bigint, 'VOCADB', $3::varchar, NULL, false
		WHERE NOT EXISTS (
			SELECT 1
			FROM vocal_links
			WHERE vocal_id = $2::bigint
			  AND vocal_link_type = 'VOCADB'
			  AND url = $3::varchar
		)`, uuidValue, vocalID, fmt.Sprintf("https://vocadb.net/Ar/%d", vocadbID))
	return err
}

func ensureVocadbSongLinkTx(tx *sql.Tx, songID int64, vocadbID int) error {
	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}
	_, err = tx.Exec(`
		INSERT INTO song_links (
			uuid,
			created_at,
			updated_at,
			song_id,
			song_link_type,
			url,
			content,
			is_deleted
		)
		SELECT $1::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2::bigint, 'VOCADB', $3::varchar, NULL, false
		WHERE NOT EXISTS (
			SELECT 1
			FROM song_links
			WHERE song_id = $2::bigint
			  AND song_link_type = 'VOCADB'
			  AND url = $3::varchar
		)`, uuidValue, songID, fmt.Sprintf("https://vocadb.net/S/%d", vocadbID))
	return err
}

func ensureSongLinkTx(tx *sql.Tx, songID int64, link NormalizedSongLink) error {
	if songID <= 0 {
		return nil
	}
	if strings.TrimSpace(link.URL) == "" {
		return nil
	}
	_, err := tx.Exec(`
		UPDATE song_links
		SET content = $1,
			is_deleted = $2,
			updated_at = CURRENT_TIMESTAMP
		WHERE song_id = $3
		  AND song_link_type = $4
		  AND url = $5`,
		sql.NullString{String: link.Description, Valid: strings.TrimSpace(link.Description) != ""},
		link.IsDeleted,
		songID,
		link.LinkType,
		link.URL,
	)
	if err != nil {
		return err
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}
	_, err = tx.Exec(`
		INSERT INTO song_links (
			uuid,
			created_at,
			updated_at,
			song_id,
			song_link_type,
			url,
			content,
			is_deleted
		)
		SELECT $1::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2::bigint, $3::varchar, $4::varchar, $5::varchar, $6::boolean
		WHERE NOT EXISTS (
			SELECT 1
			FROM song_links
			WHERE song_id = $2::bigint
			  AND song_link_type = $3::varchar
			  AND url = $4::varchar
		)`,
		uuidValue,
		songID,
		link.LinkType,
		link.URL,
		nullIfBlank(link.Description),
		link.IsDeleted,
	)
	return err
}

func ensureArtistLinkTx(tx *sql.Tx, artistID int64, link NormalizedResourceLink) error {
	if artistID <= 0 {
		return nil
	}
	if strings.TrimSpace(link.URL) == "" {
		return nil
	}
	_, err := tx.Exec(`
		UPDATE artist_links
		SET content = $1,
			is_deleted = $2,
			updated_at = CURRENT_TIMESTAMP
		WHERE artist_id = $3
		  AND artist_link_type = $4
		  AND url = $5`,
		nullIfBlank(link.Description),
		link.IsDeleted,
		artistID,
		link.LinkType,
		link.URL,
	)
	if err != nil {
		return err
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}
	_, err = tx.Exec(`
		INSERT INTO artist_links (
			uuid,
			created_at,
			updated_at,
			artist_id,
			artist_link_type,
			url,
			content,
			is_deleted
		)
		SELECT $1::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2::bigint, $3::varchar, $4::varchar, $5::varchar, $6::boolean
		WHERE NOT EXISTS (
			SELECT 1
			FROM artist_links
			WHERE artist_id = $2::bigint
			  AND artist_link_type = $3::varchar
			  AND url = $4::varchar
		)`,
		uuidValue,
		artistID,
		link.LinkType,
		link.URL,
		nullIfBlank(link.Description),
		link.IsDeleted,
	)
	return err
}

func ensureVocalLinkTx(tx *sql.Tx, vocalID int64, link NormalizedResourceLink) error {
	if vocalID <= 0 {
		return nil
	}
	if strings.TrimSpace(link.URL) == "" {
		return nil
	}
	_, err := tx.Exec(`
		UPDATE vocal_links
		SET content = $1,
			is_deleted = $2,
			updated_at = CURRENT_TIMESTAMP
		WHERE vocal_id = $3
		  AND vocal_link_type = $4
		  AND url = $5`,
		nullIfBlank(link.Description),
		link.IsDeleted,
		vocalID,
		link.LinkType,
		link.URL,
	)
	if err != nil {
		return err
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}
	_, err = tx.Exec(`
		INSERT INTO vocal_links (
			uuid,
			created_at,
			updated_at,
			vocal_id,
			vocal_link_type,
			url,
			content,
			is_deleted
		)
		SELECT $1::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2::bigint, $3::varchar, $4::varchar, $5::varchar, $6::boolean
		WHERE NOT EXISTS (
			SELECT 1
			FROM vocal_links
			WHERE vocal_id = $2::bigint
			  AND vocal_link_type = $3::varchar
			  AND url = $4::varchar
		)`,
		uuidValue,
		vocalID,
		link.LinkType,
		link.URL,
		nullIfBlank(link.Description),
		link.IsDeleted,
	)
	return err
}

func ensureDraftArtistTx(tx *sql.Tx, ref DumpSongArtistRef) (int64, bool, error) {
	if ref.ArtistID <= 0 {
		return 0, false, nil
	}
	if !isDraftableArtistType(ref.ArtistType) {
		return 0, false, nil
	}
	if err := acquireAdvisoryXactLock(tx, advisoryLockArtist, ref.ArtistID); err != nil {
		return 0, false, err
	}

	existingID, err := findExistingArtistIDByVocadbIDTx(tx, ref.ArtistID)
	if err != nil {
		return 0, false, err
	}
	if existingID > 0 {
		return existingID, false, nil
	}

	dumpArtist, err := getDumpArtistRowTx(tx, ref.ArtistID)
	if err != nil && err != sql.ErrNoRows {
		return 0, false, err
	}

	canonicalName := firstNonBlank(normalizeNullableString(dumpArtist.Name), ref.Name, fmt.Sprintf("VocaDB Artist %d", ref.ArtistID))
	thumbnailURL := extractThumbnailFromPictures(dumpArtist.PicturesJSON)

	resourceID, err := insertResourceTx(tx, canonicalName, thumbnailURL, "ARTIST")
	if err != nil {
		return 0, false, err
	}

	if err := insertPrimaryResourceNameTx(tx, resourceID, "UND", canonicalName); err != nil {
		return 0, false, err
	}
	if _, err := tx.Exec("INSERT INTO artists (id, content) VALUES ($1, NULL)", resourceID); err != nil {
		return 0, false, err
	}
	if err := ensureVocadbArtistLinkTx(tx, resourceID, ref.ArtistID); err != nil {
		return 0, false, err
	}
	if err := syncArtistWebLinksTx(tx, resourceID, dumpArtist.WebLinksJSON); err != nil {
		return 0, false, err
	}

	return resourceID, true, nil
}

func ensureArtistFromRelationRefTx(tx *sql.Tx, ref DumpArtistRelationRef) (int64, bool, error) {
	return ensureDraftArtistTx(tx, DumpSongArtistRef{
		ArtistID:   ref.ArtistID,
		Name:       ref.Name,
		ArtistType: ref.ArtistType,
		SortOrder:  ref.SortOrder,
	})
}

func markTouchedExistingArtistIDs(
	createdArtistIDSet map[int64]struct{},
	touchedExistingArtistIDs map[int64]struct{},
	artistIDs ...int64,
) {
	for _, artistID := range artistIDs {
		if artistID <= 0 {
			continue
		}
		if _, created := createdArtistIDSet[artistID]; created {
			continue
		}
		touchedExistingArtistIDs[artistID] = struct{}{}
	}
}

func syncArtistGroupsTx(
	tx *sql.Tx,
	currentArtistID int64,
	dumpArtist DumpArtistRow,
	createdArtistIDs *[]int64,
	createdArtistIDSet map[int64]struct{},
	touchedExistingArtistIDs map[int64]struct{},
) error {
	for _, ref := range parseDumpArtistRelationRefs(dumpArtist.GroupsJSON) {
		groupArtistID, created, err := ensureArtistFromRelationRefTx(tx, ref)
		if err != nil {
			return err
		}
		if created {
			*createdArtistIDs = append(*createdArtistIDs, groupArtistID)
			createdArtistIDSet[groupArtistID] = struct{}{}
		}
		inserted, err := ensureArtistGroupTx(tx, groupArtistID, currentArtistID, ref.SortOrder)
		if err != nil {
			return err
		}
		if inserted {
			markTouchedExistingArtistIDs(createdArtistIDSet, touchedExistingArtistIDs, groupArtistID, currentArtistID)
		}
	}

	for _, ref := range parseDumpArtistRelationRefs(dumpArtist.MembersJSON) {
		memberArtistID, created, err := ensureArtistFromRelationRefTx(tx, ref)
		if err != nil {
			return err
		}
		if created {
			*createdArtistIDs = append(*createdArtistIDs, memberArtistID)
			createdArtistIDSet[memberArtistID] = struct{}{}
		}
		inserted, err := ensureArtistGroupTx(tx, currentArtistID, memberArtistID, ref.SortOrder)
		if err != nil {
			return err
		}
		if inserted {
			markTouchedExistingArtistIDs(createdArtistIDSet, touchedExistingArtistIDs, currentArtistID, memberArtistID)
		}
	}

	return nil
}

func ensureDraftVocalTx(tx *sql.Tx, ref DumpSongArtistRef) (int64, bool, error) {
	if ref.ArtistID <= 0 {
		return 0, false, nil
	}
	if err := acquireAdvisoryXactLock(tx, advisoryLockVocal, ref.ArtistID); err != nil {
		return 0, false, err
	}

	existingID, err := findExistingVocalIDByVocadbIDTx(tx, ref.ArtistID)
	if err != nil {
		return 0, false, err
	}
	if existingID > 0 {
		return existingID, false, nil
	}

	dumpArtist, err := getDumpArtistRowTx(tx, ref.ArtistID)
	if err != nil && err != sql.ErrNoRows {
		return 0, false, err
	}

	canonicalName := firstNonBlank(normalizeNullableString(dumpArtist.Name), ref.Name, fmt.Sprintf("VocaDB Vocal %d", ref.ArtistID))
	thumbnailURL := extractThumbnailFromPictures(dumpArtist.PicturesJSON)

	resourceID, err := insertResourceTx(tx, canonicalName, thumbnailURL, "VOCAL")
	if err != nil {
		return 0, false, err
	}

	if err := insertPrimaryResourceNameTx(tx, resourceID, "UND", canonicalName); err != nil {
		return 0, false, err
	}
	if _, err := tx.Exec("INSERT INTO vocals (id, content) VALUES ($1, NULL)", resourceID); err != nil {
		return 0, false, err
	}
	if err := ensureVocadbVocalLinkTx(tx, resourceID, ref.ArtistID); err != nil {
		return 0, false, err
	}
	if err := syncVocalWebLinksTx(tx, resourceID, dumpArtist.WebLinksJSON); err != nil {
		return 0, false, err
	}

	return resourceID, true, nil
}

func ensureSongArtistTx(tx *sql.Tx, songID int64, artistID int64, roles []string, isMain bool, sortOrder int) error {
	if len(roles) == 0 {
		return nil
	}

	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}

	_, err = tx.Exec(`
		INSERT INTO song_artists (
			uuid,
			created_at,
			updated_at,
			song_id,
			artist_id,
			role,
			is_main,
			sort_order
		) VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2, $3, $4, $5, $6)
		ON CONFLICT DO NOTHING`,
		uuidValue, songID, artistID, pq.Array(roles), isMain, sortOrder)
	return err
}

func ensureSongVocalTx(tx *sql.Tx, songID int64, vocalID int64, isMain bool, sortOrder int) error {
	uuidValue, err := newUUIDString()
	if err != nil {
		return err
	}

	_, err = tx.Exec(`
		INSERT INTO song_vocals (
			uuid,
			created_at,
			updated_at,
			song_id,
			vocal_id,
			is_main,
			sort_order
		) VALUES ($1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, $2, $3, $4, $5)
		ON CONFLICT DO NOTHING`,
		uuidValue, songID, vocalID, isMain, sortOrder)
	return err
}

func ensureSongPVsTx(tx *sql.Tx, songID int64, vocadbID int, dumpPVs []DumpSongPV) (map[string]MappedSongPv, error) {
	mappings := make(map[string]MappedSongPv)
	for index, dumpPV := range dumpPVs {
		dbService := normalizePVServiceForDB(dumpPV.Service)
		if dbService == "" {
			dbService = detectServiceNameFromURL(dumpPV.URL)
		}
		if dbService == "" {
			continue
		}

		videoKey := extractVideoKeyForService(dbService, dumpPV.URL)
		if strings.TrimSpace(videoKey) == "" {
			continue
		}

		title := strings.TrimSpace(dumpPV.Title)

		var songPvID int64
		err := tx.QueryRow(`
			SELECT id
			FROM song_pvs
			WHERE song_id = $1
			  AND (
				(service = $2 AND video_key = $3)
				OR url = $4
			  )
			ORDER BY id ASC
			LIMIT 1`, songID, dbService, videoKey, dumpPV.URL).Scan(&songPvID)
		if err == sql.ErrNoRows {
			uuidValue, uuidErr := newUUIDString()
			if uuidErr != nil {
				return nil, uuidErr
			}

			if err = tx.QueryRow(`
				INSERT INTO song_pvs (
					uuid,
					created_at,
					updated_at,
					song_id,
					service,
					video_key,
					url,
					title,
					thumbnail_url,
					uploader_key,
					duration_seconds,
					is_official,
					is_deleted,
					published_at,
					piapro_audio_url,
					bilibili_cid,
					bandcamp_external_url,
					sort_order
				) VALUES (
					$1,
					CURRENT_TIMESTAMP,
					CURRENT_TIMESTAMP,
					$2,
					$3,
					$4,
					$5,
					$6,
					$7,
					$8,
					$9,
					true,
					false,
					$10,
					$11,
					$12,
					$13,
					$14
				)
				RETURNING id`,
				uuidValue,
				songID,
				dbService,
				videoKey,
				dumpPV.URL,
				sql.NullString{String: title, Valid: title != ""},
				sql.NullString{},
				sql.NullString{},
				sql.NullInt32{},
				sql.NullTime{},
				sql.NullString{},
				sql.NullInt64{},
				sql.NullString{},
				index,
			).Scan(&songPvID); err != nil {
				return nil, err
			}
		} else if err != nil {
			return nil, err
		} else {
			if _, err := tx.Exec(`
				UPDATE song_pvs
				SET service = $2,
					video_key = $3,
					title = COALESCE($4, title),
					thumbnail_url = COALESCE($5, thumbnail_url),
					uploader_key = COALESCE($6, uploader_key),
					duration_seconds = COALESCE($7, duration_seconds),
					published_at = COALESCE($8, published_at),
					piapro_audio_url = COALESCE($9, piapro_audio_url),
					bilibili_cid = COALESCE($10, bilibili_cid),
					bandcamp_external_url = COALESCE($11, bandcamp_external_url),
					updated_at = CURRENT_TIMESTAMP
				WHERE id = $1`,
				songPvID,
				dbService,
				videoKey,
				sql.NullString{String: title, Valid: title != ""},
				sql.NullString{},
				sql.NullString{},
				sql.NullInt32{},
				sql.NullTime{},
				sql.NullString{},
				sql.NullInt64{},
				sql.NullString{},
			); err != nil {
				return nil, err
			}
		}

		mappedSongPv := MappedSongPv{
			SongPvID: songPvID,
			Service:  dbService,
			URL:      dumpPV.URL,
		}
		if normalizedKey := makeNormalizedTaskLookupKey(vocadbID, dbService, videoKey); normalizedKey != "" {
			mappings[normalizedKey] = mappedSongPv
		}
		mappings[makeTaskKey(vocadbID, dumpPV.URL)] = mappedSongPv
	}

	return mappings, nil
}

func enrichMappedSongPvMetadata(db *sql.DB, mappedSongPv MappedSongPv) error {
	resolvedPV, err := resolveSongPVMetadata(mappedSongPv.URL)
	if err != nil {
		return err
	}
	if resolvedPV == nil {
		return nil
	}

	dbService := normalizePVServiceForDB(resolvedPV.Service)
	if dbService == "" {
		dbService = normalizePVServiceForDB(mappedSongPv.Service)
	}
	if dbService == "" {
		dbService = detectServiceNameFromURL(mappedSongPv.URL)
	}
	if dbService == "" {
		return nil
	}

	videoKey := strings.TrimSpace(resolvedPV.VideoKey)
	if videoKey == "" {
		videoKey = extractVideoKeyForService(dbService, mappedSongPv.URL)
	}
	if videoKey == "" {
		return nil
	}

	title := strings.TrimSpace(resolvedPV.Title)
	thumbnailURL := strings.TrimSpace(resolvedPV.ThumbnailURL)
	uploaderKey := strings.TrimSpace(resolvedPV.UploaderKey)

	var durationSeconds sql.NullInt32
	if resolvedPV.DurationSeconds != nil {
		durationSeconds = sql.NullInt32{Int32: int32(*resolvedPV.DurationSeconds), Valid: true}
	}

	var publishedAt sql.NullTime
	if published, ok := parsePublishedAt(strings.TrimSpace(resolvedPV.PublishedAt)).(time.Time); ok {
		publishedAt = sql.NullTime{Time: published, Valid: true}
	}

	var piaproAudioURL sql.NullString
	var bilibiliCID sql.NullInt64
	var bandcampExternalURL sql.NullString
	if resolvedPV.Extra != nil {
		piaproAudioURL = sql.NullString{
			String: strings.TrimSpace(resolvedPV.Extra.AudioURL),
			Valid:  strings.TrimSpace(resolvedPV.Extra.AudioURL) != "",
		}
		if resolvedPV.Extra.CID != nil {
			bilibiliCID = sql.NullInt64{Int64: *resolvedPV.Extra.CID, Valid: true}
		}
		bandcampExternalURL = sql.NullString{
			String: strings.TrimSpace(resolvedPV.Extra.ExternalURL),
			Valid:  strings.TrimSpace(resolvedPV.Extra.ExternalURL) != "",
		}
	}

	_, err = db.Exec(`
		UPDATE song_pvs
		SET service = $2,
			video_key = $3,
			title = COALESCE($4, title),
			thumbnail_url = COALESCE($5, thumbnail_url),
			uploader_key = COALESCE($6, uploader_key),
			duration_seconds = COALESCE($7, duration_seconds),
			published_at = COALESCE($8, published_at),
			piapro_audio_url = COALESCE($9, piapro_audio_url),
			bilibili_cid = COALESCE($10, bilibili_cid),
			bandcamp_external_url = COALESCE($11, bandcamp_external_url),
			updated_at = CURRENT_TIMESTAMP
		WHERE id = $1`,
		mappedSongPv.SongPvID,
		dbService,
		videoKey,
		sql.NullString{String: title, Valid: title != ""},
		sql.NullString{String: thumbnailURL, Valid: thumbnailURL != ""},
		sql.NullString{String: uploaderKey, Valid: uploaderKey != ""},
		durationSeconds,
		publishedAt,
		piaproAudioURL,
		bilibiliCID,
		bandcampExternalURL,
	)
	return err
}

func ensureDraftSongForVocadbID(db *sql.DB, vocadbID int) (map[string]MappedSongPv, error) {
	return ensureDraftSongForVocadbIDInternal(db, vocadbID, make(map[int]struct{}))
}

func ensureDraftSongForVocadbIDInternal(db *sql.DB, vocadbID int, seen map[int]struct{}) (map[string]MappedSongPv, error) {
	if _, exists := seen[vocadbID]; exists {
		return map[string]MappedSongPv{}, nil
	}
	seen[vocadbID] = struct{}{}
	defer delete(seen, vocadbID)

	var dumpSongType sql.NullString
	var originalVersionJSON sql.NullString
	if err := db.QueryRow(`
			SELECT song_type, original_version
			FROM dump_vocadb_song
			WHERE id = $1
			  AND COALESCE(deleted, false) = false
			LIMIT 1`, vocadbID).Scan(&dumpSongType, &originalVersionJSON); err != nil && err != sql.ErrNoRows {
		return nil, err
	}

	if !isDraftableSongType(normalizeNullableString(dumpSongType)) {
		return map[string]MappedSongPv{}, fmt.Errorf("%w: vocadb song id %d type %q", errDraftSongTypeNotAllowed, vocadbID, normalizeNullableString(dumpSongType))
	}

	originalVersionID := parseDumpOriginalVersionID(originalVersionJSON)
	if originalVersionID > 0 && originalVersionID != vocadbID {
		if _, err := ensureDraftSongForVocadbIDInternal(db, originalVersionID, seen); err != nil {
			log.Printf("Failed to precreate original version %d for VocaDB song id %d: %v", originalVersionID, vocadbID, err)
		}
	}

	tx, err := db.Begin()
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = tx.Rollback()
	}()

	if err := acquireAdvisoryXactLock(tx, advisoryLockSong, vocadbID); err != nil {
		return nil, err
	}

	dumpSong, err := getDumpSongRowTx(tx, vocadbID)
	if err != nil {
		return nil, err
	}

	songID, err := findExistingSongIDByVocadbIDTx(tx, vocadbID)
	if err != nil {
		return nil, err
	}
	songCreated := false
	touchedExistingSongIDs := make(map[int64]struct{})
	touchedExistingArtistIDs := make(map[int64]struct{})
	createdArtistIDs := make([]int64, 0)
	createdArtistIDSet := make(map[int64]struct{})
	createdVocalIDs := make([]int64, 0)

	if songID == 0 {
		canonicalName := firstNonBlank(normalizeNullableString(dumpSong.DefaultName), fmt.Sprintf("VocaDB Song %d", vocadbID))
		thumbnailURL := firstNonBlank(normalizeNullableString(dumpSong.MainPictureThumb), normalizeNullableString(dumpSong.MainPictureOriginal))
		resourceID, err := insertResourceTx(tx, canonicalName, thumbnailURL, "SONG")
		if err != nil {
			return nil, err
		}
		songID = resourceID

		if err := insertPrimaryResourceNameTx(tx, resourceID, mapVocadbLanguage(normalizeNullableString(dumpSong.DefaultNameLanguage)), canonicalName); err != nil {
			return nil, err
		}

		if _, err := tx.Exec(`
			INSERT INTO songs (id, content, published_at, song_type)
			VALUES ($1, NULL, $2, $3)`,
			resourceID,
			parsePublishedAt(normalizeNullableString(dumpSong.PublishDate)),
			mapSongType(normalizeNullableString(dumpSong.SongType)),
		); err != nil {
			return nil, err
		}
		songCreated = true
	}

	if err := ensureVocadbSongLinkTx(tx, songID, vocadbID); err != nil {
		return nil, err
	}

	if err := syncSongWebLinksTx(tx, songID, dumpSong.WebLinksJSON); err != nil {
		return nil, err
	}

	dumpArtists := parseDumpSongArtists(dumpSong.ArtistsJSON)
	hasMainArtist := false
	hasMainVocal := false
	for _, ref := range dumpArtists {
		if isVocalistType(ref.ArtistType) {
			vocalID, vocalCreated, err := ensureDraftVocalTx(tx, ref)
			if err != nil {
				return nil, err
			}
			if vocalCreated {
				createdVocalIDs = append(createdVocalIDs, vocalID)
			}
			if vocalID > 0 {
				isMain := !hasMainVocal
				if err := ensureSongVocalTx(tx, songID, vocalID, isMain, ref.SortOrder); err != nil {
					return nil, err
				}
				hasMainVocal = true
			}
			continue
		}

		artistID, artistCreated, err := ensureDraftArtistTx(tx, ref)
		if err != nil {
			return nil, err
		}
		if artistCreated {
			createdArtistIDs = append(createdArtistIDs, artistID)
			createdArtistIDSet[artistID] = struct{}{}
		}

		mappedRoles := mapArtistRoles(ref.Roles, ref.ArtistType)
		if artistID > 0 && len(mappedRoles) > 0 {
			isMain := !hasMainArtist
			if err := ensureSongArtistTx(tx, songID, artistID, mappedRoles, isMain, ref.SortOrder); err != nil {
				return nil, err
			}
			hasMainArtist = true
		}

		if artistID > 0 {
			dumpArtist, err := getDumpArtistRowTx(tx, ref.ArtistID)
			if err != nil && err != sql.ErrNoRows {
				return nil, err
			}
			if err == nil {
				if err := syncArtistGroupsTx(
					tx,
					artistID,
					dumpArtist,
					&createdArtistIDs,
					createdArtistIDSet,
					touchedExistingArtistIDs,
				); err != nil {
					return nil, err
				}
			}
		}
	}

	vocalCount, err := countSongVocalsTx(tx, songID)
	if err != nil {
		return nil, err
	}
	if vocalCount == 0 {
		return nil, fmt.Errorf("at least one vocal is required for VocaDB song id %d", vocadbID)
	}

	mappings, err := ensureSongPVsTx(tx, songID, vocadbID, parseDumpSongPVs(dumpSong.PvsJSON))
	if err != nil {
		return nil, err
	}

	forwardRelatedVocadbSongIDs := make(map[int]struct{})
	originalVersionID = parseDumpOriginalVersionID(dumpSong.OriginalVersionJSON)
	if originalVersionID > 0 && originalVersionID != vocadbID {
		forwardRelatedVocadbSongIDs[originalVersionID] = struct{}{}
	}
	childVocadbIDs, err := findChildVocadbSongIDsByOriginalVersionTx(tx, vocadbID)
	if err != nil {
		return nil, err
	}
	for relatedVocadbSongID := range forwardRelatedVocadbSongIDs {
		relatedSongID, err := findExistingSongIDByVocadbIDTx(tx, relatedVocadbSongID)
		if err != nil {
			return nil, err
		}
		if relatedSongID == 0 {
			continue
		}

		inserted, err := ensureSongRelationTx(tx, songID, relatedSongID)
		if err != nil {
			return nil, err
		}
		if inserted {
			if !songCreated {
				touchedExistingSongIDs[songID] = struct{}{}
			}
			touchedExistingSongIDs[relatedSongID] = struct{}{}
		}
	}

	for _, childVocadbID := range childVocadbIDs {
		if childVocadbID == vocadbID {
			continue
		}

		childSongID, err := findExistingSongIDByVocadbIDTx(tx, childVocadbID)
		if err != nil {
			return nil, err
		}
		if childSongID == 0 {
			continue
		}

		inserted, err := ensureSongRelationTx(tx, childSongID, songID)
		if err != nil {
			return nil, err
		}
		if inserted {
			if !songCreated {
				touchedExistingSongIDs[songID] = struct{}{}
			}
			touchedExistingSongIDs[childSongID] = struct{}{}
		}
	}

	for _, artistID := range createdArtistIDs {
		snapshot, err := buildArtistHistorySnapshotTx(tx, artistID)
		if err != nil {
			return nil, err
		}
		if err := recordCreateHistoryTx(tx, artistID, snapshot); err != nil {
			return nil, err
		}
	}

	for _, vocalID := range createdVocalIDs {
		snapshot, err := buildVocalHistorySnapshotTx(tx, vocalID)
		if err != nil {
			return nil, err
		}
		if err := recordCreateHistoryTx(tx, vocalID, snapshot); err != nil {
			return nil, err
		}
	}

	if songCreated {
		snapshot, err := buildSongHistorySnapshotTx(tx, songID)
		if err != nil {
			return nil, err
		}
		if err := recordCreateHistoryTx(tx, songID, snapshot); err != nil {
			return nil, err
		}
	}

	for touchedSongID := range touchedExistingSongIDs {
		if touchedSongID == songID && songCreated {
			continue
		}

		snapshot, err := buildSongHistorySnapshotTx(tx, touchedSongID)
		if err != nil {
			return nil, err
		}
		if err := recordUpdateHistoryTx(tx, touchedSongID, snapshot); err != nil {
			return nil, err
		}
	}

	for touchedArtistID := range touchedExistingArtistIDs {
		snapshot, err := buildArtistHistorySnapshotTx(tx, touchedArtistID)
		if err != nil {
			return nil, err
		}
		if err := recordUpdateHistoryTx(tx, touchedArtistID, snapshot); err != nil {
			return nil, err
		}
	}

	if err := tx.Commit(); err != nil {
		return nil, err
	}

	return mappings, nil
}

func fetchViewsForService(serviceName string, pvURL string) (int, bool) {
	switch strings.ToUpper(strings.TrimSpace(serviceName)) {
	case "NICONICO", "NICONICODOUGA":
		return retryWithBackoff(func() (int, bool, bool) {
			return getNicoNicoDougaViews(pvURL)
		}, pvURL, serviceName)
	case "YOUTUBE", "YOUTUBEVIDEO":
		return retryWithBackoff(func() (int, bool, bool) {
			return getYoutubeViews(pvURL)
		}, pvURL, serviceName)
	case "BILIBILI":
		return retryWithBackoff(func() (int, bool, bool) {
			return getBilibiliViews(pvURL)
		}, pvURL, serviceName)
	case "PIAPRO":
		return retryWithBackoff(func() (int, bool, bool) {
			return getPiaproViews(pvURL)
		}, pvURL, serviceName)
	case "SOUNDCLOUD":
		return retryWithBackoff(func() (int, bool, bool) {
			return getSoundCloudViews(pvURL)
		}, pvURL, serviceName)
	default:
		return 0, false
	}
}

func parseDumpWebLinks(webLinksJSON sql.NullString) ([]DumpWebLink, error) {
	if !webLinksJSON.Valid || strings.TrimSpace(webLinksJSON.String) == "" {
		return nil, nil
	}
	var links []DumpWebLink
	if err := json.Unmarshal([]byte(webLinksJSON.String), &links); err != nil {
		return nil, err
	}
	return links, nil
}

func syncSongWebLinksTx(tx *sql.Tx, songID int64, webLinksJSON sql.NullString) error {
	links, err := parseDumpWebLinks(webLinksJSON)
	if err != nil {
		return err
	}
	if len(links) == 0 {
		return nil
	}

	normalized := normalizeSongWebLinks(links)
	for _, link := range normalized {
		if err := ensureSongLinkTx(tx, songID, link); err != nil {
			return err
		}
	}
	return nil
}

func syncArtistWebLinksTx(tx *sql.Tx, artistID int64, webLinksJSON sql.NullString) error {
	links, err := parseDumpWebLinks(webLinksJSON)
	if err != nil {
		return err
	}
	if len(links) == 0 {
		return nil
	}

	normalized := normalizeArtistWebLinks(links)
	for _, link := range normalized {
		if err := ensureArtistLinkTx(tx, artistID, link); err != nil {
			return err
		}
	}
	return nil
}

func syncVocalWebLinksTx(tx *sql.Tx, vocalID int64, webLinksJSON sql.NullString) error {
	links, err := parseDumpWebLinks(webLinksJSON)
	if err != nil {
		return err
	}
	if len(links) == 0 {
		return nil
	}

	normalized := normalizeVocalWebLinks(links)
	for _, link := range normalized {
		if err := ensureVocalLinkTx(tx, vocalID, link); err != nil {
			return err
		}
	}
	return nil
}

func normalizeSongWebLinks(links []DumpWebLink) []NormalizedSongLink {
	normalized := make([]NormalizedSongLink, 0)
	seen := make(map[string]struct{})

	for _, link := range links {
		urlValue := strings.TrimSpace(link.URL)
		if urlValue == "" {
			continue
		}
		parsed, err := url.Parse(urlValue)
		if err != nil {
			continue
		}
		desc := strings.TrimSpace(link.Description)
		host := strings.ToLower(parsed.Host)

		appendLink := func(linkType, description string) {
			key := linkType + "|" + description + "|" + urlValue
			if _, exists := seen[key]; exists {
				return
			}
			seen[key] = struct{}{}
			normalized = append(normalized, NormalizedSongLink{
				LinkType:    linkType,
				Description: description,
				URL:         urlValue,
				IsDeleted:   link.Disabled,
			})
		}

		if strings.HasSuffix(host, ".fanbox.cc") || host == "fanbox.cc" || host == "www.fanbox.cc" {
			appendLink("PIXIV", "Pixiv (Fanbox)")
			continue
		}
		if strings.HasSuffix(host, ".tumblr.com") && host != "tumblr.com" {
			appendLink("TUMBLR", "Tumblr (Blog)")
			continue
		}

		switch host {
		case "piapro.jp":
			if isPiaproInstrumental(desc) {
				appendLink("PIAPRO", "Piapro (Instrumental)")
			} else if isPiaproLyrics(desc) {
				appendLink("PIAPRO", "Piapro (Lyrics)")
			} else if isPiaproIllustration(desc) {
				appendLink("PIAPRO", "Piapro (Illustration)")
			}
		case "soundcloud.com":
			if isSoundCloudInstrumental(desc) {
				appendLink("SOUNDCLOUD", "SoundCloud (Instrumental)")
			}
		case "www5.atwiki.jp":
			appendLink("ATWIKI", "初音ミク Wiki")
		case "w.atwiki.jp":
			if strings.Contains(parsed.Path, "/hmiku/") {
				appendLink("ATWIKI", "初音ミク Wiki")
			}
		case "www.pixiv.net":
			appendLink("PIXIV", "Pixiv")
		case "music.163.com", "y.music.163.com":
			if isNetEaseInstrumental(desc) {
				appendLink("NETEASE_MUSIC", "NCM Instrumental")
			} else if isNetEaseAlbum(desc) {
				appendLink("NETEASE_MUSIC", "NCM Album")
			} else if isNetEaseRelease(desc) {
				appendLink("NETEASE_MUSIC", "NCM Release")
			}
		case "x.com":
			if isXIllustration(desc) {
				appendLink("X", "X (Illustration)")
			} else if isXDefault(desc) {
				appendLink("X", "X")
			}
		case "open.spotify.com":
			if label, ok := classifySpotifyPath(parsed.Path); ok {
				appendLink("SPOTIFY", label)
			}
		case "music.apple.com":
			if label, ok := classifyAppleMusicPath(parsed.Path); ok {
				appendLink("APPLE_MUSIC", label)
			}
		case "utaitedb.net":
			if isUtaiteDBOriginal(desc) {
				appendLink("UTAITEDB", "UtaiteDB (Original)")
			}
		case "www.youtube.com", "youtu.be":
			if isYouTubeInstrumental(desc) {
				appendLink("YOUTUBE", "YouTube (Instrumental)")
			} else if isYouTubeOriginal(desc) {
				appendLink("YOUTUBE", "YouTube (Original)")
			}
		case "www.nicovideo.jp":
			if isNicoNicoInstrumental(desc) {
				appendLink("NICONICO", "NicoNico (Instrumental)")
			} else if isNicoNicoOriginal(desc) {
				appendLink("NICONICO", "NicoNico (Original)")
			}
		case "dic.nicovideo.jp":
			if desc == "NicoNicoPedia" {
				appendLink("NICONICO_PEDIA", "NicoNicoPedia")
			}
		case "commons.nicovideo.jp":
			if isNicommonsInstrumental(desc) {
				appendLink("NICOMMONS", "Nicommons (Instrumental)")
			} else if isNicommonsIllustration(desc) {
				appendLink("NICOMMONS", "Nicommons (Illustration)")
			}
		}
	}

	return normalized
}

func normalizeArtistWebLinks(links []DumpWebLink) []NormalizedResourceLink {
	return normalizeArtistLikeWebLinks(links)
}

func normalizeVocalWebLinks(links []DumpWebLink) []NormalizedResourceLink {
	return normalizeArtistLikeWebLinks(links)
}

func normalizeArtistLikeWebLinks(links []DumpWebLink) []NormalizedResourceLink {
	normalized := make([]NormalizedResourceLink, 0)
	seen := make(map[string]struct{})

	for _, link := range links {
		urlValue := strings.TrimSpace(link.URL)
		if urlValue == "" {
			continue
		}
		parsed, err := url.Parse(urlValue)
		if err != nil {
			continue
		}
		desc := strings.TrimSpace(link.Description)
		host := normalizeHost(parsed.Host)
		path := parsed.Path
		parts := splitPath(path)

		appendLink := func(linkType, description string) {
			key := linkType + "|" + description + "|" + urlValue
			if _, exists := seen[key]; exists {
				return
			}
			seen[key] = struct{}{}
			normalized = append(normalized, NormalizedResourceLink{
				LinkType:    linkType,
				Description: description,
				URL:         urlValue,
				IsDeleted:   link.Disabled,
			})
		}

		switch {
		case host == "piapro.jp":
			if isPiaproUserPath(parts) {
				appendLink("PIAPRO", "Piapro (User)")
			}
		case host == "pixiv.net" || host == "www.pixiv.net":
			if isPixivUserPath(parts) {
				appendLink("PIXIV", "Pixiv (User)")
			}
		case strings.HasSuffix(host, "fanbox.cc"):
			appendLink("PIXIV", "Pixiv (Fanbox)")
		case host == "soundcloud.com":
			if isSoundCloudPlaylistPath(parts) {
				appendLink("SOUNDCLOUD", "SoundCloud (Playlist)")
			} else if isSoundCloudUserPath(parts) {
				appendLink("SOUNDCLOUD", "SoundCloud (User)")
			}
		case host == "instagram.com":
			if isInstagramUserPath(parts) {
				appendLink("INSTAGRAM", "Instagram (User)")
			}
		case host == "twitch.tv":
			if isTwitchUserPath(parts) {
				appendLink("TWITCH", "Twitch (User)")
			}
		case host == "space.bilibili.com":
			if len(parts) >= 1 && parts[0] != "" {
				appendLink("BILIBILI", "Bilibili (Space)")
			}
		case host == "utaitedb.net":
			if isUtaiteDBArtistPath(parts) {
				appendLink("UTAITEDB", "UtaiteDB (Artist)")
			}
		case host == "facebook.com":
			if isFacebookPagePath(parts) {
				appendLink("FACEBOOK", "Facebook (Page)")
			} else if isFacebookProfilePath(parts) {
				appendLink("FACEBOOK", "Facebook (Profile)")
			}
		case host == "skeb.jp":
			if isSkebUserPath(parts) {
				appendLink("SKEB", "Skeb (User)")
			}
		case host == "open.spotify.com" || host == "play.spotify.com":
			if label, ok := classifySpotifyArtistPath(parts); ok {
				appendLink("SPOTIFY", label)
			}
		case host == "tiktok.com":
			if isTikTokUserPath(parts) {
				appendLink("TIKTOK", "TikTok (User)")
			}
		case strings.HasSuffix(host, ".tumblr.com"):
			appendLink("TUMBLR", "Tumblr (Blog)")
		case host == "tumblr.com":
			if isTumblrBlogPath(parts) {
				appendLink("TUMBLR", "Tumblr (Blog)")
			}
		case host == "bowlroll.net":
			if isBowlRollFilePath(parts) {
				appendLink("BOWLROLL", "BowlRoll (File)")
			} else if isBowlRollUserPath(parts) {
				appendLink("BOWLROLL", "BowlRoll (User)")
			}
		case host == "utau.fandom.com":
			appendLink("FANDOM", "UTAU Wiki")
		case host == "utau.wikia.com":
			appendLink("FANDOM", "UTAU Wiki (Wikia)")
		case host == "vocaloid.wikia.com":
			appendLink("FANDOM", "Vocaloid Wiki (Wikia)")
		case host == "weibo.com":
			if isWeiboProfilePath(parts) {
				appendLink("WEIBO", "Weibo (Profile)")
			}
		case host == "twpf.jp":
			appendLink("TWPF", "Twpf")
		case host == "www5.atwiki.jp":
			if strings.Contains(path, "/hmiku/") {
				appendLink("ATWIKI", "初音ミク Wiki")
			}
		case host == "ameblo.jp":
			if isAmebloUserPath(parts) {
				appendLink("AMEBLO", "Ameblo")
			}
		case host == "bsky.app":
			if len(parts) >= 2 && parts[0] == "profile" {
				appendLink("BLUESKY", "Bluesky")
			}
		case host == "discogs.com":
			if label, ok := classifyDiscogsPath(parts); ok {
				appendLink("DISCOGS", label)
			}
		case host == "music.163.com" || host == "y.music.163.com":
			if label, ok := classifyNetEaseArtistPath(parts, parsed.RawQuery, parsed.Fragment); ok {
				appendLink("NETEASE_MUSIC", label)
			}
		case host == "tunecore.co.jp":
			if isTuneCoreArtistPath(parts, parsed.RawQuery) {
				appendLink("TUNECORE", "TuneCore (Artist)")
			}
		case host == "vgmdb.net":
			if label, ok := classifyVgmdbPath(parts); ok {
				appendLink("VGMDB", label)
			}
		case host == "ko-fi.com":
			if isSimpleUserPath(parts) {
				appendLink("KOFI", "Ko-fi")
			}
		case host == "ja.wikipedia.org":
			if isWikipediaPath(parts) {
				appendLink("WIKIPEDIA", "Wikipedia (JA)")
			}
		case host == "en.wikipedia.org":
			if isWikipediaPath(parts) {
				appendLink("WIKIPEDIA", "Wikipedia (EN)")
			}
		case host == "sites.google.com":
			if isGoogleSitesPath(parts) {
				appendLink("GOOGLE_SITES", "Google Sites")
			}
		case host == "utaudatabase.wiki.fc2.com":
			appendLink("FC2_WIKI", "UTAU Database Wiki")
		case host == "utau.wikidot.com":
			appendLink("WIKIDOT", "UTAU Wikidot")
		case host == "utau.wiki":
			appendLink("UTAU_WIKI", "UTAU Wiki")
		case host == "vocaloidlyrics.miraheze.org":
			appendLink("MIRAHEZE", "Vocaloid Lyrics Wiki")
		case host == "linktr.ee":
			if isSimpleUserPath(parts) {
				appendLink("LINKTREE", "Linktree")
			}
		case host == "note.com":
			if isSimpleUserPath(parts) {
				appendLink("NOTE", "note")
			}
		case host == "karent.jp":
			if len(parts) >= 2 && parts[0] == "artist" {
				appendLink("KARENT", "KARENT (Artist)")
			}
		case host == "musicbrainz.org":
			if label, ok := classifyMusicBrainzPath(parts); ok {
				appendLink("MUSICBRAINZ", label)
			}
		case host == "music.apple.com":
			if label, ok := classifyAppleMusicArtistPath(parts); ok {
				appendLink("APPLE_MUSIC", label)
			}
		case host == "touhoudb.com":
			if len(parts) >= 2 && strings.EqualFold(parts[0], "Ar") {
				appendLink("TOUHOUDB", "TouhouDB (Artist)")
			}
		case host == "deviantart.com":
			if isDeviantArtUserPath(parts) {
				appendLink("DEVIANTART", "DeviantArt (User)")
			}
		case host == "lit.link":
			if isSimpleUserPath(parts) {
				appendLink("LITLINK", "lit.link")
			}
		case strings.HasSuffix(host, "youtube.com"):
			if label, ok := classifyYouTubeArtistPath(parts); ok {
				appendLink("YOUTUBE", label)
			}
		case host == "x.com" || host == "twitter.com":
			if isXDefault(desc) && isXHandlePath(parts) {
				appendLink("X", "X (Twitter)")
			}
		case host == "nicovideo.jp" || host == "com.nicovideo.jp":
			if label, ok := classifyNicoNicoArtistPath(parts); ok {
				appendLink("NICONICO", label)
			}
		case host == "dic.nicovideo.jp":
			if len(parts) >= 1 && (parts[0] == "a" || parts[0] == "id") {
				appendLink("NICONICO", "NicoNicoPedia")
			}
		case host == "seiga.nicovideo.jp":
			if len(parts) >= 2 && parts[0] == "user" && parts[1] == "illust" {
				appendLink("NICONICO", "NicoNico Seiga (Illustration)")
			}
		}
	}

	return normalized
}

func normalizeHost(host string) string {
	normalized := strings.ToLower(strings.TrimSpace(host))
	normalized = strings.TrimPrefix(normalized, "www.")
	normalized = strings.TrimPrefix(normalized, "m.")
	normalized = strings.TrimPrefix(normalized, "mobile.")
	return normalized
}

func isPiaproUserPath(parts []string) bool {
	return len(parts) >= 1 && parts[0] != ""
}

func isPixivUserPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	if parts[0] == "users" && len(parts) >= 2 {
		return true
	}
	return parts[0] != ""
}

func isSoundCloudPlaylistPath(parts []string) bool {
	return len(parts) >= 2 && parts[1] == "sets"
}

func isSoundCloudUserPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	switch parts[0] {
	case "discover", "search", "stream", "you", "charts", "settings":
		return false
	default:
		return true
	}
}

func isInstagramUserPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	switch parts[0] {
	case "p", "reel", "tv", "explore", "stories":
		return false
	default:
		return parts[0] != ""
	}
}

func isTwitchUserPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	switch parts[0] {
	case "directory", "videos", "downloads", "jobs", "p", "settings":
		return false
	default:
		return parts[0] != ""
	}
}

func isUtaiteDBArtistPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	switch strings.ToLower(parts[0]) {
	case "artist", "artists", "ar":
		return true
	default:
		return false
	}
}

func isFacebookProfilePath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	if parts[0] == "profile.php" {
		return true
	}
	return parts[0] != "" && parts[0] != "pages"
}

func isFacebookPagePath(parts []string) bool {
	return len(parts) >= 1 && parts[0] == "pages"
}

func isSkebUserPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	if strings.HasPrefix(parts[0], "@") {
		return true
	}
	return parts[0] != ""
}

func classifySpotifyArtistPath(parts []string) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	kind := parts[0]
	if strings.HasPrefix(kind, "intl-") && len(parts) > 1 {
		kind = parts[1]
	}
	switch kind {
	case "artist":
		return "Spotify (Artist)", true
	case "album":
		return "Spotify (Album)", true
	case "playlist":
		return "Spotify (Playlist)", true
	default:
		return "", false
	}
}

func isTikTokUserPath(parts []string) bool {
	return len(parts) >= 1 && strings.HasPrefix(parts[0], "@")
}

func isTumblrBlogPath(parts []string) bool {
	return len(parts) >= 1 && parts[0] != "" && parts[0] != "tagged"
}

func isBowlRollFilePath(parts []string) bool {
	return len(parts) >= 1 && parts[0] == "file"
}

func isBowlRollUserPath(parts []string) bool {
	return len(parts) >= 1 && parts[0] == "user"
}

func isWeiboProfilePath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	if parts[0] == "u" && len(parts) >= 2 {
		return true
	}
	return parts[0] != ""
}

func isAmebloUserPath(parts []string) bool {
	return len(parts) >= 1 && parts[0] != ""
}

func classifyDiscogsPath(parts []string) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	switch parts[0] {
	case "artist":
		return "Discogs (Artist)", true
	case "label":
		return "Discogs (Label)", true
	default:
		return "", false
	}
}

func classifyNetEaseArtistPath(parts []string, rawQuery string, fragment string) (string, bool) {
	joined := strings.ToLower(strings.Join(parts, "/"))
	switch {
	case strings.Contains(joined, "artist"):
		return "NCM Artist", true
	case strings.Contains(joined, "album"):
		return "NCM Album", true
	case strings.Contains(joined, "playlist"):
		return "NCM Playlist", true
	}
	fragmentLower := strings.ToLower(fragment)
	switch {
	case strings.Contains(fragmentLower, "artist"):
		return "NCM Artist", true
	case strings.Contains(fragmentLower, "album"):
		return "NCM Album", true
	case strings.Contains(fragmentLower, "playlist"):
		return "NCM Playlist", true
	}
	query := strings.ToLower(rawQuery)
	if strings.Contains(query, "id=") {
		switch {
		case strings.Contains(joined, "artist"):
			return "NCM Artist", true
		case strings.Contains(joined, "album"):
			return "NCM Album", true
		case strings.Contains(joined, "playlist"):
			return "NCM Playlist", true
		}
	}
	return "", false
}

func isTuneCoreArtistPath(parts []string, rawQuery string) bool {
	if len(parts) == 0 {
		return false
	}
	if parts[0] == "artist" || parts[0] == "artists" {
		return true
	}
	return strings.Contains(strings.ToLower(rawQuery), "artists")
}

func classifyVgmdbPath(parts []string) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	switch parts[0] {
	case "artist":
		return "VGMdb (Artist)", true
	case "album":
		return "VGMdb (Album)", true
	default:
		return "", false
	}
}

func isSimpleUserPath(parts []string) bool {
	return len(parts) >= 1 && parts[0] != ""
}

func isWikipediaPath(parts []string) bool {
	return len(parts) >= 2 && parts[0] == "wiki"
}

func isGoogleSitesPath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	return parts[0] == "view" || parts[0] == "site"
}

func classifyMusicBrainzPath(parts []string) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	switch parts[0] {
	case "artist":
		return "MusicBrainz (Artist)", true
	case "label":
		return "MusicBrainz (Label)", true
	default:
		return "", false
	}
}

func classifyAppleMusicArtistPath(parts []string) (string, bool) {
	if len(parts) < 2 {
		return "", false
	}
	country := parts[0]
	kind := parts[1]
	switch kind {
	case "artist":
		return fmt.Sprintf("Apple Music (%s) (Artist)", country), true
	case "album":
		return fmt.Sprintf("Apple Music (%s) (Album)", country), true
	default:
		return "", false
	}
}

func isDeviantArtUserPath(parts []string) bool {
	return len(parts) >= 1 && parts[0] != ""
}

func classifyYouTubeArtistPath(parts []string) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	if strings.HasPrefix(parts[0], "@") {
		return "YouTube (Handle)", true
	}
	switch parts[0] {
	case "channel":
		if len(parts) >= 2 {
			return "YouTube (Channel)", true
		}
	case "user":
		if len(parts) >= 2 {
			return "YouTube (User)", true
		}
	case "c":
		if len(parts) >= 2 {
			return "YouTube (Custom)", true
		}
	}
	return "", false
}

func isXHandlePath(parts []string) bool {
	if len(parts) == 0 {
		return false
	}
	switch parts[0] {
	case "home", "i", "intent", "search", "share":
		return false
	default:
		return parts[0] != ""
	}
}

func classifyNicoNicoArtistPath(parts []string) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	switch parts[0] {
	case "user":
		return "NicoNico (User)", true
	case "mylist":
		return "NicoNico (Mylist)", true
	case "channel":
		return "NicoNico (Channel)", true
	case "series":
		return "NicoNico (Series)", true
	case "community":
		return "NicoNico (Community)", true
	default:
		return "", false
	}
}

func isPiaproInstrumental(desc string) bool {
	l := strings.ToLower(desc)
	if strings.Contains(l, "karaoke") || strings.Contains(l, "karoake") ||
		strings.Contains(l, "off vocal") || strings.Contains(l, "offvo") ||
		strings.Contains(l, "instrumental") || strings.Contains(l, "no vocal") ||
		strings.Contains(l, "without vocal") || strings.Contains(l, "without main vocal") ||
		strings.Contains(l, "without bass") || strings.Contains(l, "backing track") ||
		strings.Contains(l, "chorus only") || strings.Contains(l, "voiceless") ||
		strings.Contains(l, "drumless") || strings.Contains(l, "inst.") {
		return true
	}
	return strings.Contains(desc, "カラオケ") || strings.Contains(desc, "オケ") ||
		strings.Contains(desc, "オフボーカル") || strings.Contains(desc, "ハモリなし") ||
		strings.Contains(desc, "抜きVer") || strings.Contains(desc, "コーラス付") ||
		strings.Contains(desc, "男性キー") || strings.Contains(desc, "音声無し") ||
		strings.Contains(desc, "メインボーカル無し") || strings.Contains(desc, "ドラムレス") ||
		strings.Contains(desc, "ガイド用クリック")
}

func isPiaproLyrics(desc string) bool {
	l := strings.ToLower(desc)
	return strings.Contains(l, "lyric") || strings.Contains(l, "lyrics") ||
		strings.Contains(desc, "歌詞") || strings.Contains(l, "translation")
}

func isPiaproIllustration(desc string) bool {
	l := strings.ToLower(desc)
	return strings.Contains(l, "illustr") || strings.Contains(l, "illust") ||
		strings.Contains(l, "illus.") || strings.Contains(l, "ilustration") ||
		strings.Contains(l, "image") || strings.Contains(l, "artwork") ||
		strings.Contains(l, "logo") || strings.Contains(l, "pixel art") ||
		strings.Contains(l, "cover art") || strings.Contains(l, "background") ||
		strings.Contains(l, "avatar") || strings.Contains(l, "photograph") ||
		strings.Contains(desc, "各パートmp3")
}

func isSoundCloudInstrumental(desc string) bool {
	l := strings.ToLower(desc)
	return strings.Contains(l, "instrumental") || strings.Contains(l, "inst") ||
		strings.Contains(l, "off vocal") || strings.Contains(l, "off-vocal") ||
		strings.Contains(l, "offvocal") || strings.Contains(l, "karaoke") ||
		strings.Contains(l, "no vocal") || strings.Contains(l, "without vocal") ||
		strings.Contains(l, "without main vocal") || strings.Contains(l, "offvo")
}

func isNetEaseInstrumental(desc string) bool {
	return hasAny(desc, []string{
		"instrumental", "inst", "off vocal", "off-vocal", "karaoke", "カラオケ", "オフボーカル", "offvo",
	})
}

func isNetEaseAlbum(desc string) bool {
	return strings.Contains(strings.ToLower(desc), "album")
}

func isNetEaseRelease(desc string) bool {
	l := strings.ToLower(desc)
	if strings.Contains(l, "release") || strings.Contains(l, "song release") ||
		strings.Contains(l, "single release") || strings.Contains(l, "digital release") ||
		strings.Contains(l, "mp3") {
		return true
	}
	return strings.Contains(desc, "网易云音乐") ||
		desc == "NCM Song Release" ||
		desc == "NCM Song Release (Album ver.)" ||
		desc == "NCM Song Release (blocked both inside and outside China?)" ||
		desc == "NCM Song Release - Instrumental" ||
		desc == "NCM Song Release (off vocal)" ||
		desc == "NCM Song Release (instrumental)" ||
		desc == "NCM Song Release (Instrumental - female version)" ||
		desc == "NCM Song Release (Instrumental - male version)" ||
		desc == "NCM Song Release (Instrumental)" ||
		desc == "NCM Album Release" ||
		desc == "NCM Song Release (inst)"
}

func isXIllustration(desc string) bool {
	return hasAny(desc, []string{
		"illustration", "illustrations", "illust", "artwork", "cover", "image", "art",
		"イラスト", "絵", "画像", "ジャケット",
	})
}

func isXDefault(desc string) bool {
	return desc == "" || desc == "X" || desc == "Twitter" || desc == "X (Twitter)"
}

func classifySpotifyPath(path string) (string, bool) {
	parts := splitPath(path)
	if len(parts) == 0 {
		return "", false
	}
	kind := parts[0]
	if strings.HasPrefix(kind, "intl-") && len(parts) > 1 {
		kind = parts[1]
	}
	switch kind {
	case "track":
		return "Spotify", true
	case "album":
		return "Spotify (Album)", true
	case "playlist":
		return "Spotify (Playlist)", true
	default:
		return "", false
	}
}

func classifyAppleMusicPath(path string) (string, bool) {
	parts := splitPath(path)
	if len(parts) < 2 {
		return "", false
	}
	country := parts[0]
	kind := parts[1]
	switch kind {
	case "album":
		return fmt.Sprintf("Apple Music (%s) (Album)", country), true
	case "playlist":
		return fmt.Sprintf("Apple Music (%s) (Playlist)", country), true
	case "music-video":
		return fmt.Sprintf("Apple Music (%s) (Music Video)", country), true
	default:
		return "", false
	}
}

func isUtaiteDBOriginal(desc string) bool {
	l := strings.ToLower(desc)
	return strings.Contains(l, "original") || strings.Contains(l, "original song") ||
		strings.Contains(l, "original ver") || strings.Contains(l, "original version")
}

func isYouTubeInstrumental(desc string) bool {
	return hasAny(desc, []string{
		"instrumental", "inst", "off vocal", "off-vocal", "karaoke", "カラオケ", "オフボーカル", "offvo",
	})
}

func isYouTubeOriginal(desc string) bool {
	l := strings.ToLower(desc)
	return strings.Contains(l, "original") || strings.Contains(l, "original song") ||
		strings.Contains(l, "original ver") || strings.Contains(l, "original version")
}

func isNicoNicoInstrumental(desc string) bool {
	return hasAny(desc, []string{
		"instrumental", "inst", "off vocal", "off-vocal", "karaoke", "カラオケ", "オフボーカル", "offvo",
	})
}

func isNicoNicoOriginal(desc string) bool {
	l := strings.ToLower(desc)
	return strings.Contains(l, "original") || strings.Contains(l, "original song") ||
		strings.Contains(l, "original ver") || strings.Contains(l, "original version")
}

func isNicommonsInstrumental(desc string) bool {
	return hasAny(desc, []string{
		"instrumental", "inst", "off vocal", "off-vocal", "karaoke", "カラオケ", "オフボーカル", "offvo", "chorus",
	})
}

func isNicommonsIllustration(desc string) bool {
	return hasAny(desc, []string{
		"illustration", "illust", "image", "artwork", "background",
		"イラスト", "絵", "画像", "背景",
	})
}

func hasAny(desc string, needles []string) bool {
	l := strings.ToLower(desc)
	for _, needle := range needles {
		if strings.Contains(l, strings.ToLower(needle)) {
			return true
		}
		if strings.Contains(desc, needle) {
			return true
		}
	}
	return false
}

func splitPath(path string) []string {
	parts := strings.Split(path, "/")
	cleaned := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		cleaned = append(cleaned, part)
	}
	return cleaned
}

// requeueTaskToRedis puts a task back into the given Redis batch when processing should be retried.
// We append to the tail so the original queue order is preserved for untouched tasks.
func requeueTaskToRedis(redisKey string, task QueuedSongTask) {
	ctx := context.Background()

	if task.Retry+1 >= MaxTaskRetries {
		log.Printf("Dropping task for vocadb %d after %d retries", task.VocadbID, task.Retry+1)
		return
	}

	task.Retry++
	newValue, err := encodeQueuedSongTask(task)
	if err != nil {
		log.Printf("Failed to encode retry task for vocadb %d: %v", task.VocadbID, err)
		return
	}
	if err := redisClient.RPush(ctx, redisKey, newValue).Err(); err != nil {
		log.Printf("Failed to requeue task into Redis key %s: %v", redisKey, err)
	}
}

func filterRefreshRetryTask(
	task QueuedSongTask,
	mappedSongPVsByTask map[string]MappedSongPv,
	today time.Time,
	successSet map[int64]struct{},
	successSetMutex *sync.RWMutex,
) (QueuedSongTask, bool) {
	if len(task.PVs) == 0 {
		return task, false
	}

	songPvIDs := make([]int64, 0, len(task.PVs))
	pvIndexBySongPvID := make(map[int64]int)
	needsRetry := make([]bool, len(task.PVs))

	for idx, pv := range task.PVs {
		pvURL := strings.TrimSpace(pv.URL)
		if pvURL == "" {
			continue
		}
		serviceName := firstNonBlank(normalizePVServiceForDB(pv.Service), detectServiceNameFromURL(pvURL))
		lookupKey := makeTaskLookupKey(task.VocadbID, serviceName, pvURL)
		rawKey := makeTaskKey(task.VocadbID, pvURL)

		mappedSongPv, ok := mappedSongPVsByTask[lookupKey]
		if !ok {
			mappedSongPv, ok = mappedSongPVsByTask[rawKey]
		}
		if !ok {
			// No mapping yet: keep retrying.
			needsRetry[idx] = true
			continue
		}
		if _, exists := pvIndexBySongPvID[mappedSongPv.SongPvID]; !exists {
			pvIndexBySongPvID[mappedSongPv.SongPvID] = idx
			songPvIDs = append(songPvIDs, mappedSongPv.SongPvID)
		}
	}

	if len(songPvIDs) == 0 {
		filtered := make([]DumpSongPV, 0, len(task.PVs))
		for idx, pv := range task.PVs {
			if needsRetry[idx] {
				filtered = append(filtered, pv)
			}
		}
		task.PVs = filtered
		return task, len(task.PVs) > 0
	}

	successSetMutex.RLock()
	succeededToday := make(map[int64]struct{}, len(songPvIDs))
	for _, songPvID := range songPvIDs {
		if _, ok := successSet[songPvID]; ok {
			succeededToday[songPvID] = struct{}{}
		}
	}
	successSetMutex.RUnlock()

	for songPvID, idx := range pvIndexBySongPvID {
		if _, ok := succeededToday[songPvID]; !ok {
			needsRetry[idx] = true
		}
	}

	filtered := make([]DumpSongPV, 0, len(task.PVs))
	for idx, pv := range task.PVs {
		if needsRetry[idx] {
			filtered = append(filtered, pv)
		}
	}
	task.PVs = filtered
	return task, len(task.PVs) > 0
}

func readTorControlReply(reader *bufio.Reader) (string, error) {
	var lines []string
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			return strings.Join(lines, ""), err
		}
		lines = append(lines, line)

		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(trimmed, "250 ") ||
			strings.HasPrefix(trimmed, "5") ||
			strings.HasPrefix(trimmed, "515") {
			return strings.Join(lines, ""), nil
		}
	}
}

func writeTorControlCommand(conn net.Conn, command string) error {
	if _, err := io.WriteString(conn, command); err != nil {
		return err
	}
	return nil
}

func authenticateTorControl(conn net.Conn, reader *bufio.Reader) error {
	cookiePath := getEnv("TOR_CONTROL_COOKIE_FILE", "/etc/tor/run/control.authcookie")
	if cookieBytes, err := os.ReadFile(cookiePath); err == nil && len(cookieBytes) > 0 {
		command := fmt.Sprintf("AUTHENTICATE %s\r\n", strings.ToUpper(hex.EncodeToString(cookieBytes)))
		if err := writeTorControlCommand(conn, command); err != nil {
			return err
		}
		reply, err := readTorControlReply(reader)
		if err != nil {
			return err
		}
		if strings.Contains(reply, "250 OK") {
			return nil
		}
		return fmt.Errorf("tor control cookie authentication failed")
	}

	for _, command := range []string{"AUTHENTICATE\r\n", "AUTHENTICATE \"\"\r\n"} {
		if err := writeTorControlCommand(conn, command); err != nil {
			return err
		}
		reply, err := readTorControlReply(reader)
		if err != nil {
			return err
		}
		if strings.Contains(reply, "250 OK") {
			return nil
		}
	}
	return fmt.Errorf("tor control authentication failed")
}

func fetchTorBootstrapProgress(controlAddr string) (int, string, error) {
	conn, err := net.DialTimeout("tcp", controlAddr, 5*time.Second)
	if err != nil {
		return 0, "", err
	}
	defer conn.Close()

	if err := conn.SetDeadline(time.Now().Add(5 * time.Second)); err != nil {
		return 0, "", err
	}

	reader := bufio.NewReader(conn)
	if err := authenticateTorControl(conn, reader); err != nil {
		return 0, "", err
	}

	if err := writeTorControlCommand(conn, "GETINFO status/bootstrap-phase\r\n"); err != nil {
		return 0, "", err
	}
	reply, err := readTorControlReply(reader)
	if err != nil {
		return 0, "", err
	}

	_ = writeTorControlCommand(conn, "QUIT\r\n")

	match := torBootstrapProgress.FindStringSubmatch(reply)
	if len(match) != 2 {
		return 0, reply, fmt.Errorf("tor bootstrap progress not found in reply")
	}

	progress, err := strconv.Atoi(match[1])
	if err != nil {
		return 0, reply, err
	}

	return progress, reply, nil
}

func waitForTorBootstrap() error {
	controlAddr := getEnv("TOR_CONTROL_ADDR", "127.0.0.1:9051")
	log.Printf("Waiting for Tor bootstrap to reach 100%% via control port %s...", controlAddr)

	deadline := time.Now().Add(TorBootstrapTimeout)
	lastProgress := -1
	var lastErr error

	for time.Now().Before(deadline) {
		progress, reply, err := fetchTorBootstrapProgress(controlAddr)
		if err == nil {
			if progress != lastProgress {
				log.Printf("Tor bootstrap progress: %d%%", progress)
				lastProgress = progress
			}
			if progress >= 100 {
				log.Println("Tor bootstrap reached 100%, proceeding with requests")
				return nil
			}
			lastErr = fmt.Errorf("tor bootstrap still in progress: %s", strings.TrimSpace(reply))
		} else {
			lastErr = err
			log.Printf("Tor bootstrap check failed: %v", err)
		}

		time.Sleep(TorBootstrapPollDelay)
	}

	if lastErr == nil {
		lastErr = fmt.Errorf("timed out waiting for tor bootstrap")
	}
	return lastErr
}

var (
	torNewnymMu   sync.Mutex
	lastTorNewnym time.Time
)

func shouldRotateTorForService(serviceName string) bool {
	switch strings.ToUpper(strings.TrimSpace(serviceName)) {
	case "NICONICO", "NICONICODOUGA", "PIAPRO", "YOUTUBE", "YOUTUBEVIDEO":
		return true
	default:
		return false
	}
}

func requestTorNewnym(reason string) {
	if strings.TrimSpace(os.Getenv("TOR_SOCKS_PROXY")) == "" {
		return
	}
	if strings.EqualFold(strings.TrimSpace(getEnv("TOR_NEWNYM_ENABLED", "true")), "false") {
		return
	}

	minInterval := getEnvDuration("TOR_NEWNYM_MIN_INTERVAL", 600*time.Second)

	torNewnymMu.Lock()
	if !lastTorNewnym.IsZero() && time.Since(lastTorNewnym) < minInterval {
		torNewnymMu.Unlock()
		return
	}
	torNewnymMu.Unlock()

	controlAddr := getEnv("TOR_CONTROL_ADDR", "127.0.0.1:9051")
	conn, err := net.DialTimeout("tcp", controlAddr, 5*time.Second)
	if err != nil {
		log.Printf("Tor NEWNYM failed to connect control port %s: %v", controlAddr, err)
		return
	}
	defer conn.Close()

	if err := conn.SetDeadline(time.Now().Add(5 * time.Second)); err != nil {
		log.Printf("Tor NEWNYM failed to set deadline: %v", err)
		return
	}

	reader := bufio.NewReader(conn)
	if err := authenticateTorControl(conn, reader); err != nil {
		log.Printf("Tor NEWNYM authentication failed: %v", err)
		return
	}

	if err := writeTorControlCommand(conn, "SIGNAL NEWNYM\r\n"); err != nil {
		log.Printf("Tor NEWNYM command failed: %v", err)
		return
	}
	reply, err := readTorControlReply(reader)
	if err != nil {
		log.Printf("Tor NEWNYM reply failed: %v", err)
		return
	}
	_ = writeTorControlCommand(conn, "QUIT\r\n")

	if !strings.Contains(reply, "250 OK") {
		log.Printf("Tor NEWNYM rejected: %s", strings.TrimSpace(reply))
		return
	}

	torNewnymMu.Lock()
	lastTorNewnym = time.Now()
	torNewnymMu.Unlock()

	log.Printf("Tor NEWNYM requested: %s", reason)
}

func main() {
	// Wait for Tor network to be ready if configured
	if os.Getenv("TOR_SOCKS_PROXY") != "" {
		if err := waitForTorBootstrap(); err != nil {
			log.Fatalf("Tor bootstrap did not reach 100%%: %v", err)
		}
	}

	// Initialize Redis
	initRedis()
	defer redisClient.Close()

	ctx := context.Background()

	db, err := sql.Open("postgres", getDBConnectionString())
	if err != nil {
		log.Fatalf("sql open failed: %v", err)
	}
	defer db.Close()

	// optimize database connection pool to reduce memory usage
	db.SetMaxOpenConns(5)                  // Reduced from 10
	db.SetMaxIdleConns(2)                  // Reduced from 5
	db.SetConnMaxLifetime(5 * time.Minute) // Set lifetime to prevent stale connections

	mappedSongPVsByTask, err := loadMappedSongPVsByTask(db)
	if err != nil {
		log.Fatalf("failed to load song PV mappings: %v", err)
	}
	log.Printf("Found %d VocaDB-linked song PV mappings", len(mappedSongPVsByTask))
	var mappedSongPVsMutex sync.RWMutex

	historyHintsBySongPvID, err := loadSongPVHistoryHints(db)
	if err != nil {
		log.Fatalf("failed to load song PV history hints: %v", err)
	}
	log.Printf("Loaded %d song PV history hints", len(historyHintsBySongPvID))

	type ViewInsert struct {
		SongPvID    int64
		Views       int
		HistoryHint *SongPVHistoryHint
	}

	currentDate := utcDateString()
	seedRedisKey := getSeedRedisKey()
	refreshRedisKey := getRefreshRedisKey(currentDate)
	seedCursorKey := getRedisCursorKey(seedRedisKey)
	refreshCursorKey := getRedisCursorKey(refreshRedisKey)
	mode := "seed"
	refreshOnly := false
	if strings.EqualFold(strings.TrimSpace(os.Getenv("INSERT_COUNT_START_MODE")), "refresh") {
		mode = "refresh"
		refreshOnly = true
	}
	youtubeMinInterval = SeedYouTubeMinInterval
	// ctx already initialized above for lock refresh.

	flushBatch := func(viewInserts *[]ViewInsert, batchMutex *sync.Mutex) {
		batchMutex.Lock()
		defer batchMutex.Unlock()

		if len(*viewInserts) == 0 {
			return
		}

		tx, err := db.Begin()
		if err != nil {
			log.Printf("failed to begin transaction: %v", err)
			*viewInserts = (*viewInserts)[:0]
			return
		}

		today := dateOnlyUTC(time.Now())

		for _, vi := range *viewInserts {
			var latest latestSongPVView
			latestFound := false

			if vi.HistoryHint != nil {
				if hintDate, ok := parseHistoryHintDate(vi.HistoryHint.LastSuccessDate); ok {
					latest.date = hintDate
					latest.viewCount = vi.HistoryHint.LastSuccessViews
					latestFound = true
				}
			}

			if !latestFound {
				err := tx.QueryRow(`
					SELECT id, created_at::date, view_count
					FROM song_pv_views
					WHERE song_pv_id = $1
					  AND is_failed = false
					ORDER BY created_at DESC, id DESC
					LIMIT 1
				`, vi.SongPvID).Scan(&latest.id, &latest.date, &latest.viewCount)
				if err != nil && err != sql.ErrNoRows {
					log.Printf("failed to load latest song_pv_views row for song_pv_id %d: %v", vi.SongPvID, err)
					continue
				}
				if err == nil {
					latestFound = true
				}
			}

			if latestFound && dateOnlyUTC(latest.date).Equal(today) {
				if err := upsertSongPVViewForDateTx(tx, vi.SongPvID, today, vi.Views, false); err != nil {
					log.Printf("failed to upsert today's song_pv_views row for song_pv_id %d: %v", vi.SongPvID, err)
				}
				continue
			}

			if latestFound {
				latestDate := dateOnlyUTC(latest.date)
				yesterday := today.AddDate(0, 0, -1)
				if latestDate.Before(yesterday) {
					missingDays := int(today.Sub(latestDate).Hours()/24) - 1
					if missingDays > 0 {
						for dayOffset := 1; dayOffset <= missingDays; dayOffset++ {
							missingDate := latestDate.AddDate(0, 0, dayOffset)
							backfillViews := trendAwareBackfillViewCount(
								latest.viewCount,
								vi.Views,
								dayOffset,
								missingDays+1,
								vi.HistoryHint,
							)
							if err := upsertSongPVViewForDateTx(tx, vi.SongPvID, missingDate, backfillViews, true); err != nil {
								log.Printf(
									"failed to backfill song_pv_views row for song_pv_id %d on %s: %v",
									vi.SongPvID,
									missingDate.Format("2006-01-02"),
									err,
								)
							}
						}
					}
				}
			}

			if err := upsertSongPVViewForDateTx(tx, vi.SongPvID, today, vi.Views, false); err != nil {
				log.Printf("failed to insert today's song_pv_views row for song_pv_id %d: %v", vi.SongPvID, err)
			}
		}

		if err := tx.Commit(); err != nil {
			log.Printf("failed to commit transaction: %v", err)
		}

		if cap(*viewInserts) > DBBatchSize*3 {
			*viewInserts = make([]ViewInsert, 0, DBBatchSize)
		} else {
			*viewInserts = (*viewInserts)[:0]
		}
	}

	processBatch := func(redisKey string, allowMaterialization bool, forceRefresh bool, today time.Time, currentDate string) error {
		pendingCount, err := redisClient.LLen(ctx, redisKey).Result()
		if err != nil {
			return fmt.Errorf("failed to count Redis tasks: %w", err)
		}
		if pendingCount == 0 {
			return nil
		}

		log.Printf("Processing %d tasks from Redis key: %s", pendingCount, redisKey)

		requestLimit := SeedMaxConcurrentRequests
		pvLimit := SeedMaxConcurrentPVs
		youtubeLimit := SeedMaxConcurrentYouTube
		if forceRefresh {
			requestLimit = RefreshMaxConcurrentRequests
			pvLimit = RefreshMaxConcurrentPVs
			youtubeLimit = RefreshMaxConcurrentYouTube
		}

		songSemaphore := make(chan struct{}, requestLimit)
		pvSemaphore := make(chan struct{}, pvLimit)
		youtubeSemaphore := make(chan struct{}, youtubeLimit)

		successSet := make(map[int64]struct{})
		var successSetMutex sync.RWMutex
		if forceRefresh {
			rows, err := db.Query(`
				SELECT DISTINCT song_pv_id
				FROM song_pv_views
				WHERE created_at::date = $1
				  AND is_failed = false
			`, today.Format("2006-01-02"))
			if err != nil {
				log.Printf("Failed to load today's successful song_pv_views: %v", err)
			} else {
				for rows.Next() {
					var songPvID int64
					if err := rows.Scan(&songPvID); err != nil {
						log.Printf("Failed to scan successful song_pv_views: %v", err)
						continue
					}
					successSet[songPvID] = struct{}{}
				}
				if err := rows.Err(); err != nil {
					log.Printf("Failed to iterate successful song_pv_views: %v", err)
				}
				rows.Close()
			}
		}

		var wg sync.WaitGroup
		songCounter := 0
		viewInserts := make([]ViewInsert, 0, DBBatchSize)
		var batchMutex sync.Mutex

		for {
			songSemaphore <- struct{}{}

			taskValue, err := redisClient.LPop(ctx, redisKey).Result()
			if err == redis.Nil {
				<-songSemaphore
				break
			}
			if err != nil {
				<-songSemaphore
				return fmt.Errorf("failed to pop task from Redis: %w", err)
			}

			if !forceRefresh && utcDateString() != currentDate {
				if err := redisClient.RPush(ctx, redisKey, taskValue).Err(); err != nil {
					<-songSemaphore
					return fmt.Errorf("failed to requeue task after date change: %w", err)
				}
				<-songSemaphore
				return nil
			}

			task, err := parseSongTaskPayload(taskValue)
			if err != nil {
				<-songSemaphore
				log.Printf("Failed to parse song task from Redis: %v", err)
				continue
			}
			task.Raw = taskValue

			songCounter++
			if songCounter%GCInterval == 0 {
				runtime.GC()
			}

			wg.Add(1)
			go func(task QueuedSongTask) {
				defer wg.Done()
				defer func() { <-songSemaphore }()

				shouldRequeueTask := false
				failedInTask := false

				type fetchedPV struct {
					URL          string
					ServiceName  string
					Views        int
					LookupKey    string
					RawKey       string
					MappedSongPv MappedSongPv
					HasMapping   bool
					HistoryHint  *SongPVHistoryHint
				}

				vocadbID := task.VocadbID
				fetched := make([]fetchedPV, 0, len(task.PVs))
				requiresMaterialization := false

				for _, dumpPV := range task.PVs {
					pvURL := strings.TrimSpace(dumpPV.URL)
					if pvURL == "" {
						continue
					}

					serviceName := firstNonBlank(normalizePVServiceForDB(dumpPV.Service), detectServiceNameFromURL(pvURL))
					lookupKey := makeTaskLookupKey(vocadbID, serviceName, pvURL)
					rawKey := makeTaskKey(vocadbID, pvURL)

					mappedSongPVsMutex.RLock()
					mappedSongPv, ok := mappedSongPVsByTask[lookupKey]
					if !ok {
						mappedSongPv, ok = mappedSongPVsByTask[rawKey]
					}
					mappedSongPVsMutex.RUnlock()

					if ok {
						serviceName = mappedSongPv.Service
					}
					if serviceName == "" {
						log.Printf("Unsupported PV URL %s for VocaDB song id %d, skipping within song task", pvURL, vocadbID)
						if forceRefresh {
							failedInTask = true
						}
						continue
					}

					pvSemaphore <- struct{}{}
					if serviceName == "YOUTUBE" {
						youtubeSemaphore <- struct{}{}
					}
					views, success := fetchViewsForService(serviceName, pvURL)
					if serviceName == "YOUTUBE" {
						<-youtubeSemaphore
					}
					<-pvSemaphore
					if !success {
						log.Printf("Failed to fetch views for %s URL %s", serviceName, pvURL)
						if forceRefresh {
							failedInTask = true
						}
						continue
					}

					log.Printf("Successfully fetched views for %s URL %s: %d views", serviceName, pvURL, views)
					fetched = append(fetched, fetchedPV{
						URL:          pvURL,
						ServiceName:  serviceName,
						Views:        views,
						LookupKey:    lookupKey,
						RawKey:       rawKey,
						MappedSongPv: mappedSongPv,
						HasMapping:   ok,
						HistoryHint:  dumpPV.HistoryHint,
					})
					if allowMaterialization && !ok && views >= MinViewCount {
						requiresMaterialization = true
					}
				}

				if allowMaterialization && requiresMaterialization {
					newMappings, err := ensureDraftSongForVocadbID(db, vocadbID)
					if err != nil {
						if errors.Is(err, errDraftSongTypeNotAllowed) {
							log.Printf("Skipping draft creation for VocaDB song id %d because song type is not allowed: %v", vocadbID, err)
							return
						}
						shouldRequeueTask = true
						log.Printf("Failed to create draft song for VocaDB song id %d: %v", vocadbID, err)
						return
					}

					mappedSongPVsMutex.Lock()
					for key, value := range newMappings {
						mappedSongPVsByTask[key] = value
					}
					mappedSongPVsMutex.Unlock()
				}

				for _, result := range fetched {
					mappedSongPv := result.MappedSongPv
					ok := result.HasMapping
					if !ok && allowMaterialization && requiresMaterialization {
						mappedSongPVsMutex.RLock()
						mappedSongPv, ok = mappedSongPVsByTask[result.LookupKey]
						if !ok {
							mappedSongPv, ok = mappedSongPVsByTask[result.RawKey]
						}
						mappedSongPVsMutex.RUnlock()
					}
					if !ok {
						if result.Views < MinViewCount {
							log.Printf("Skipping draft creation for VocaDB song id %d URL %s because %d < %d", vocadbID, result.URL, result.Views, MinViewCount)
						}
						if forceRefresh {
							failedInTask = true
						}
						continue
					}

					if result.Views >= MinViewCount {
						if err := enrichMappedSongPvMetadata(db, mappedSongPv); err != nil {
							log.Printf("Failed to enrich song_pv metadata for vocadb song %d url %s: %v", vocadbID, result.URL, err)
						}
					}

					batchMutex.Lock()
					viewInserts = append(viewInserts, ViewInsert{
						SongPvID:    mappedSongPv.SongPvID,
						Views:       result.Views,
						HistoryHint: result.HistoryHint,
					})

					if len(viewInserts) >= DBBatchSize {
						batchMutex.Unlock()
						flushBatch(&viewInserts, &batchMutex)
						runtime.GC()
					} else {
						batchMutex.Unlock()
					}

					if forceRefresh {
						successSetMutex.Lock()
						successSet[mappedSongPv.SongPvID] = struct{}{}
						successSetMutex.Unlock()
					}
				}

				if forceRefresh && failedInTask {
					filteredTask, ok := filterRefreshRetryTask(task, mappedSongPVsByTask, today, successSet, &successSetMutex)
					if ok {
						task = filteredTask
						shouldRequeueTask = true
					}
				}

				if shouldRequeueTask {
					requeueTaskToRedis(redisKey, task)
				}
			}(task)
		}

		wg.Wait()
		flushBatch(&viewInserts, &batchMutex)
		runtime.GC()
		return nil
	}

	for {
		today := utcDateString()
		if today != currentDate {
			oldRefreshKey := refreshRedisKey
			oldRefreshCursor := refreshCursorKey

			currentDate = today
			seedRedisKey = getSeedRedisKey()
			refreshRedisKey = getRefreshRedisKey(currentDate)
			seedCursorKey = getRedisCursorKey(seedRedisKey)
			refreshCursorKey = getRedisCursorKey(refreshRedisKey)
			mode = "refresh"
			youtubeMinInterval = RefreshYouTubeMinInterval

			if err := redisClient.Del(ctx, oldRefreshKey, oldRefreshCursor).Err(); err != nil && err != redis.Nil {
				log.Printf("Failed to clear previous refresh keys: %v", err)
			}
		}

		todayDate, ok := parseUTCDate(currentDate)
		if !ok {
			todayDate = dateOnlyUTC(time.Now())
		}

		activeRedisKey := seedRedisKey
		activeCursorKey := seedCursorKey
		allowMaterialization := true
		forceRefresh := false
		if mode == "refresh" {
			activeRedisKey = refreshRedisKey
			activeCursorKey = refreshCursorKey
			allowMaterialization = false
			forceRefresh = true
			youtubeMinInterval = RefreshYouTubeMinInterval
		} else {
			youtubeMinInterval = SeedYouTubeMinInterval
		}

		pendingCount, err := redisClient.LLen(ctx, activeRedisKey).Result()
		if err != nil {
			log.Fatalf("failed to count Redis tasks: %v", err)
		}

		if pendingCount == 0 {
			if mode == "refresh" {
				loaded, lastLoadedID, err := loadNextRefreshBatchFromDB(db, activeRedisKey, activeCursorKey, todayDate, mappedSongPVsByTask, historyHintsBySongPvID)
				if err != nil {
					log.Fatalf("failed to load refresh task batch: %v", err)
				}
				if loaded == 0 {
					// Double-check refresh queue to avoid switching back too early.
					loaded, lastLoadedID, err = loadNextRefreshBatchFromDB(db, activeRedisKey, activeCursorKey, todayDate, mappedSongPVsByTask, historyHintsBySongPvID)
					if err != nil {
						log.Fatalf("failed to load refresh task batch: %v", err)
					}
				}
				if loaded == 0 {
					if err := redisClient.Del(ctx, activeCursorKey).Err(); err != nil && err != redis.Nil {
						log.Printf("Failed to delete Redis cursor %s: %v", activeCursorKey, err)
					}
					log.Println("All refresh tasks processed for today")
					if refreshOnly {
						log.Println("Refresh-only mode enabled; exiting after refresh completion")
						return
					}
					mode = "seed"
					continue
				}
				log.Printf("Loaded %d refresh tasks into Redis key %s (cursor=%d)", loaded, activeRedisKey, lastLoadedID)
			} else {
				loaded, lastLoadedID, err := loadNextTaskBatchFromDB(db, activeRedisKey, activeCursorKey, mappedSongPVsByTask, historyHintsBySongPvID)
				if err != nil {
					log.Fatalf("failed to load next task batch: %v", err)
				}
				if loaded == 0 {
					if err := redisClient.Del(ctx, activeCursorKey).Err(); err != nil && err != redis.Nil {
						log.Printf("Failed to delete Redis cursor %s: %v", activeCursorKey, err)
					}
					log.Println("All seed tasks processed, waiting for refresh window")
					time.Sleep(10 * time.Second)
					continue
				}
				log.Printf("Loaded %d tasks into Redis key %s (cursor=%d)", loaded, activeRedisKey, lastLoadedID)
			}
		}

		if err := processBatch(activeRedisKey, allowMaterialization, forceRefresh, todayDate, currentDate); err != nil {
			log.Fatalf("batch processing failed: %v", err)
		}
	}
}

type NicoNicoThumbResponse struct {
	XMLName xml.Name `xml:"nicovideo_thumb_response"`
	Status  string   `xml:"status,attr"`
	Thumb   Thumb    `xml:"thumb"`
}

type Thumb struct {
	VideoID     string `xml:"video_id"`
	Title       string `xml:"title"`
	Description string `xml:"description"`
	ViewCounter int    `xml:"view_counter"`
}

// readLimitedBody reads HTTP response body with size limit to prevent OOM
func readLimitedBody(r io.Reader) ([]byte, error) {
	limitedReader := io.LimitReader(r, MaxHTTPResponseSize)
	body, err := io.ReadAll(limitedReader)
	if err != nil {
		return nil, err
	}
	// Explicitly clear the reader if possible
	if closer, ok := r.(io.Closer); ok {
		closer.Close()
	}
	return body, nil
}

// retryWithBackoff executes a function with exponential backoff retry logic
// Only retries on network errors, not on application-level errors (404, 410, deleted videos, etc.)
func retryWithBackoff(fn func() (int, bool, bool), url string, serviceName string) (int, bool) {
	var lastViews int
	var lastSuccess bool

	for attempt := 1; attempt <= MaxRetries; attempt++ {
		views, success, shouldRetry := fn()

		if success {
			return views, true
		}

		lastViews = views
		lastSuccess = success

		// If shouldRetry is false (e.g., 404, video deleted), don't retry
		if !shouldRetry {
			log.Printf("Non-retryable error for %s URL %s (e.g., 404, video deleted)", serviceName, url)
			if shouldRotateTorForService(serviceName) {
				requestTorNewnym(fmt.Sprintf("%s non-retryable", serviceName))
			}
			return lastViews, lastSuccess
		}

		// Don't retry on the last attempt
		if attempt < MaxRetries {
			// Exponential backoff: baseDelay * 2^(attempt-1)
			backoffDelay := BaseRetryDelay * time.Duration(1<<uint(attempt-1))
			log.Printf("Retrying %s request for URL %s (attempt %d/%d) after %v", serviceName, url, attempt+1, MaxRetries, backoffDelay)
			time.Sleep(backoffDelay)
		}
	}

	log.Printf("Failed to get views for %s URL %s after %d attempts", serviceName, url, MaxRetries)
	if shouldRotateTorForService(serviceName) {
		requestTorNewnym(fmt.Sprintf("%s retries exhausted", serviceName))
	}
	return lastViews, lastSuccess
}

// getNicoNicoDougaViews returns (views, success, shouldRetry).
// We read the original watch page because getthumbinfo can report DELETED for
// videos that are still available on the site.
func getNicoNicoDougaViews(url string) (int, bool, bool) {
	videoID := extractNicoNicoVideoID(url)
	if videoID == "" {
		return 0, false, false // Invalid URL, don't retry
	}

	watchURL := fmt.Sprintf("https://www.nicovideo.jp/watch/%s", videoID)

	req, err := http.NewRequest("GET", watchURL, nil)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
	req.Header.Set("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")

	resp, err := httpClient.Do(req)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	defer resp.Body.Close()

	// 404, 410 = video deleted/not found, don't retry
	if resp.StatusCode == http.StatusNotFound || resp.StatusCode == http.StatusGone {
		return 0, false, false
	}

	if resp.StatusCode != http.StatusOK {
		log.Printf("NICONICO watch page returned HTTP %d for %s", resp.StatusCode, watchURL)
		return 0, false, true
	}

	body, err := readLimitedBody(resp.Body)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	bodyString := html.UnescapeString(string(body))
	body = nil

	if strings.Contains(bodyString, `"isDeleted":true`) ||
		strings.Contains(bodyString, `>削除された動画<`) ||
		strings.Contains(bodyString, `>この動画は削除されました<`) {
		return 0, false, false
	}

	for _, re := range nicoNicoViewPatterns {
		matches := re.FindStringSubmatch(bodyString)
		if len(matches) < 2 {
			continue
		}
		viewCount, err := strconv.Atoi(matches[1])
		if err != nil {
			continue
		}
		bodyString = ""
		return viewCount, true, false
	}

	bodyPreview := strings.TrimSpace(bodyString)
	if len(bodyPreview) > 200 {
		bodyPreview = bodyPreview[:200]
	}
	log.Printf("NICONICO watch page view count not found for %s: %s", watchURL, bodyPreview)
	return 0, false, true
}

func extractNicoNicoVideoID(url string) string {
	parts := strings.Split(url, "/")
	lastPart := parts[len(parts)-1]
	lastPart = strings.Split(lastPart, "?")[0]
	if strings.HasPrefix(lastPart, "sm") || strings.HasPrefix(lastPart, "nm") || strings.HasPrefix(lastPart, "so") {
		return lastPart
	}
	return ""
}

// getYoutubeViews returns (views, success, shouldRetry)
// shouldRetry is false when video is unavailable/deleted/private
func getYoutubeViews(url string) (int, bool, bool) {
	videoID := extractYoutubeVideoID(url)
	if videoID == "" {
		return 0, false, false // Invalid URL, don't retry
	}

	waitForYouTubeRequestWindow()

	standardURL := fmt.Sprintf("https://www.youtube.com/watch?v=%s", videoID)
	ctx, cancel := context.WithTimeout(context.Background(), HTTPClientTimeout)
	defer cancel()

	cmd := exec.CommandContext(
		ctx,
		"yt-dlp",
		"--dump-single-json",
		"--skip-download",
		"--no-warnings",
		"--no-call-home",
		"--proxy",
		"",
		"--socket-timeout",
		"15",
		standardURL,
	)
	cmd.Env = withoutProxyEnv(os.Environ())

	output, err := cmd.Output()
	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			return 0, false, true
		}

		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			stderr := strings.ToLower(string(exitErr.Stderr))
			if isYouTubeNonRetryableError(stderr) {
				return 0, false, false
			}
		}
		return 0, false, true
	}

	var info ytDlpVideoInfo
	if err := json.Unmarshal(output, &info); err != nil {
		return 0, false, true
	}

	if info.ViewCount == nil {
		return 0, false, false
	}

	return *info.ViewCount, true, false
}

func isYouTubeNonRetryableError(stderr string) bool {
	if stderr == "" {
		return false
	}

	nonRetryablePhrases := []string{
		"video unavailable",
		"this video is unavailable",
		"private video",
		"this video is private",
		"this video has been removed",
		"video has been removed",
		"unsupported url",
		"unable to extract",
		"login required",
		"sign in to confirm your age",
	}

	for _, phrase := range nonRetryablePhrases {
		if strings.Contains(stderr, phrase) {
			return true
		}
	}

	return false
}

func withoutProxyEnv(env []string) []string {
	filtered := make([]string, 0, len(env))
	for _, entry := range env {
		key, _, found := strings.Cut(entry, "=")
		if !found {
			filtered = append(filtered, entry)
			continue
		}

		switch strings.ToUpper(key) {
		case "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "all_proxy", "no_proxy", "TOR_SOCKS_PROXY":
			continue
		default:
			filtered = append(filtered, entry)
		}
	}
	return filtered
}

func waitForYouTubeRequestWindow() {
	youtubeRequestMu.Lock()
	defer youtubeRequestMu.Unlock()

	now := time.Now()
	if now.Before(youtubeNextRequestAt) {
		time.Sleep(youtubeNextRequestAt.Sub(now))
	}

	youtubeNextRequestAt = time.Now().Add(youtubeMinInterval)
}

func extractYoutubeVideoID(url string) string {
	if matches := youtubeVideoIDRegex.FindStringSubmatch(url); len(matches) > 1 {
		return matches[1]
	}
	return ""
}

type BilibiliAPIResponse struct {
	Code    int          `json:"code"`
	Message string       `json:"message"`
	Ttl     int          `json:"ttl"`
	Data    BilibiliData `json:"data"`
}

type BilibiliData struct {
	Bvid string       `json:"bvid"`
	Aid  int          `json:"aid"`
	Stat BilibiliStat `json:"stat"`
}

type BilibiliStat struct {
	Aid      int `json:"aid"`
	View     int `json:"view"`
	Danmaku  int `json:"danmaku"`
	Reply    int `json:"reply"`
	Favorite int `json:"favorite"`
	Coin     int `json:"coin"`
	Share    int `json:"share"`
	Like     int `json:"like"`
}

// getBilibiliViews returns (views, success, shouldRetry)
// shouldRetry is false when video is deleted/not found (code != 0)
func getBilibiliViews(url string) (int, bool, bool) {
	var videoID string
	var idType string

	if matches := bilibiliAVRegex.FindStringSubmatch(url); len(matches) > 1 {
		videoID = strings.TrimPrefix(matches[1], "av")
		idType = "aid"
	} else if matches := bilibiliBVRegex.FindStringSubmatch(url); len(matches) > 1 {
		videoID = matches[1]
		idType = "bvid"
	} else {
		return 0, false, false // Invalid URL, don't retry
	}

	apiURL := ""
	if idType == "aid" {
		apiURL = fmt.Sprintf("https://api.bilibili.com/x/web-interface/view?aid=%s", videoID)
	} else if idType == "bvid" {
		apiURL = fmt.Sprintf("https://api.bilibili.com/x/web-interface/view?bvid=%s", videoID)
	} else {
		return 0, false, false // Invalid ID type, don't retry
	}

	req, err := http.NewRequest("GET", apiURL, nil)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
	req.Header.Set("Referer", "https://www.bilibili.com")

	resp, err := httpClient.Do(req)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		// 5xx errors = temporary issues, should retry
		if resp.StatusCode >= 500 {
			return 0, false, true
		}
		return 0, false, false
	}

	bodyBytes, err := readLimitedBody(resp.Body)
	if err != nil {
		return 0, false, true // Network error, should retry
	}

	var bilibiliResponse BilibiliAPIResponse
	err = json.Unmarshal(bodyBytes, &bilibiliResponse)
	if err != nil {
		return 0, false, true // Parse error, might be temporary
	}

	if bilibiliResponse.Code != 0 {
		// Common codes: -400 (invalid request), -404 (not found), 62002 (video not exist)
		// These are not temporary errors, don't retry
		return 0, false, false
	}

	return bilibiliResponse.Data.Stat.View, true, false
}

// getPiaproViews returns (views, success, shouldRetry)
// shouldRetry is false when page is not found (404)
func getPiaproViews(url string) (int, bool, bool) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

	resp, err := httpClient.Do(req)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	defer resp.Body.Close()

	// 404 = content deleted, don't retry
	if resp.StatusCode == http.StatusNotFound {
		return 0, false, false
	}

	if resp.StatusCode != http.StatusOK {
		// 5xx errors = temporary issues, should retry
		if resp.StatusCode >= 500 {
			return 0, false, true
		}
		return 0, false, false
	}

	bodyBytes, err := readLimitedBody(resp.Body)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	bodyString := string(bodyBytes)
	bodyBytes = nil // Clear immediately

	matches := piaproViewPattern.FindStringSubmatch(bodyString)

	if len(matches) > 1 {
		viewCountStr := matches[1]

		viewCountStr = strings.ReplaceAll(viewCountStr, ",", "")
		viewCount, err := strconv.Atoi(viewCountStr)
		if err != nil {
			bodyString = ""
			return 0, false, false // Parse error, don't retry
		}
		bodyString = "" // Clear before returning
		return viewCount, true, false
	} else {
		bodyString = ""
		return 0, false, false // No view count found, content might be deleted
	}
}

// getSoundCloudViews returns (views, success, shouldRetry)
// shouldRetry is false when track is not found (404)
func getSoundCloudViews(url string) (int, bool, bool) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0")

	// Create a client with redirect handling for SoundCloud
	client := &http.Client{
		Timeout: HTTPClientTimeout,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 10 {
				return fmt.Errorf("stopped after 10 redirects")
			}
			return nil
		},
		Transport: httpClient.Transport,
	}
	resp, err := client.Do(req)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	defer resp.Body.Close()

	// 404 = track deleted, don't retry
	if resp.StatusCode == http.StatusNotFound {
		return 0, false, false
	}

	if resp.StatusCode != http.StatusOK {
		// 5xx errors = temporary issues, should retry
		if resp.StatusCode >= 500 {
			return 0, false, true
		}
		return 0, false, false
	}

	bodyBytes, err := readLimitedBody(resp.Body)
	if err != nil {
		return 0, false, true // Network error, should retry
	}
	bodyString := string(bodyBytes)
	bodyBytes = nil // Clear immediately

	matchesMetaSc := soundCloudPattern.FindStringSubmatch(bodyString)
	if len(matchesMetaSc) > 1 {
		viewCountStr := matchesMetaSc[1]
		viewCount, err := strconv.Atoi(viewCountStr)
		if err != nil {
			bodyString = ""
			return 0, false, false // Parse error, don't retry
		}
		bodyString = "" // Clear before returning
		return viewCount, true, false
	}

	bodyString = ""
	return 0, false, false // No view count found, track might be deleted
}
