package ru.method.linkenvisuals.managers.misc;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.waypoint.Waypoint;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.world.WorldUtils;
import lombok.Getter;
import ru.method.linkenvisuals.modules.api.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import ru.method.linkenvisuals.utils.render.DepthRenderScope;


public class WaypointManager implements Wrapper {

    private static final Pattern EVENT_LABELED_PATTERN = Pattern.compile("(?i)x\\s*[:=]?\\s*(-?\\d+)\\D+?y\\s*[:=]?\\s*(-?\\d+)\\D+?z\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern EVENT_XZ_LABELED_PATTERN = Pattern.compile("(?i)x\\s*[:=]?\\s*(-?\\d+)\\D+?z\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern EVENT_COORDS_AFTER_KEYWORD_PATTERN = Pattern.compile("(?iu)(?:coords?|coordinates?|координат\\p{L}*)\\D+?(-?\\d+)\\D+?(-?\\d+)(?:\\D+?(-?\\d+))?");
    private static final Pattern EVENT_GENERIC_PATTERN = Pattern.compile("(-?\\d+)\\s*[ ,;|/]\\s*(-?\\d+)\\s*[ ,;|/]\\s*(-?\\d+)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("\\[([^\\]]+)]");
    private static final Pattern EVENT_NAME_PIPES_PATTERN = Pattern.compile("\\|\\|\\|\\s*([^|]+?)\\s*\\|\\|\\|");
    // Текстура метки — статикой: LinkenVisuals.id() на каждый вейпоинт каждый кадр парсил Identifier заново.
    private static final net.minecraft.util.Identifier WAY_TEXTURE = LinkenVisuals.id("client/waypoint/way.png");
    private static final long EVENT_MARKER_TTL_MS = 20L * 60L * 1000L;
    private static final long EVENT_NAME_WINDOW_MS = 15_000L;
    private static final float MARKER_TEXT_EDGE_MARGIN = 4f;
    private static final float MARKER_TEXT_TOP_MARGIN = 24f;
    private static final float MARKER_TEXT_BOTTOM_MARGIN = 48f;
    private static final float MARKER_TEXT_SCOREBOARD_MARGIN = 150f;
    private static final String[] COORD_KEYWORD_TOKENS = {
            "coords",
            "coordinate",
            "coordinates",
            "\u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442"
    };
    private static final String[] EVENT_TOKENS = {
            "event",
            "coords",
            "coordinate",
            "coordinates",
            "\u0438\u0432\u0435\u043d\u0442",
            "\u043c\u0430\u044f\u043a",
            "\u0441\u0443\u043d\u0434\u0443\u043a",
            "\u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442"
    };
    private static final String[] FT_EVENT_TOKENS = {
            "ft",
            "funtime",
            "fun time",
            "\u0444\u0442",
            "\u0444\u0430\u043d\u0442\u0430\u0439\u043c"
    };

    public WaypointManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    @Getter private final List<Waypoint> waypoints = new ArrayList<>();
    private Vec3d lastEventPosition;
    private String lastEventName;
    private long lastEventAtMs;
    private boolean lastEventFt;
    private String lastEventNameCandidate;
    private long lastEventNameCandidateAtMs;
    private Waypoint eventWaypoint;
    private long eventWaypointExpiresAtMs;
    private ClientWorld lastWorld;
    // Измерение, в котором пойман ивент — метка показывается только в нём и переживает уход/возврат.
    private net.minecraft.registry.RegistryKey<net.minecraft.world.World> eventWorldKey;

    public void add(Waypoint waypoint) {
        waypoints.add(waypoint);
    }

    public void remove(Waypoint waypoint) {
        waypoints.remove(waypoint);
    }

    public void clear() {
        if (!waypoints.isEmpty()) waypoints.clear();
    }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public Waypoint getWaypoint(String name) {
        for (Waypoint waypoint : waypoints) {
            if (waypoint.getName().equalsIgnoreCase(name)) {
                return waypoint;
            }
        }
        return null;
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (Module.fullNullCheck()) return;
        if (waypoints.isEmpty()) return;

        // Метку ивента рисуем только в её измерении (в чужом мире координаты не имеют смысла), но из памяти
        // не удаляем — при возврате в нужный мир она снова отрисуется.
        boolean eventDimOk = eventWorldKey == null
                || (mc.world != null && eventWorldKey.equals(mc.world.getRegistryKey()));

        for (Waypoint waypoint : waypoints) {
            if (waypoint == eventWaypoint && !eventDimOk) continue;
            Vec3d waypointPos = new Vec3d(waypoint.getX(), waypoint.getY(), waypoint.getZ());
            double distance = mc.player.getEntityPos().distanceTo(waypointPos);

            
            renderWaypointImage(e, waypointPos, waypoint, distance);
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        ClientWorld world = mc.world;
        if (world != lastWorld) {
            // НЕ стираем метку ивента при смене мира: она привязана к измерению (eventWorldKey),
            // переживает уход и возвращается при возврате (пока не истёк TTL). Видимость гейтится
            // по измерению в onRender2D. Раньше здесь был clearEventState() — отсюда и баг.
            lastWorld = world;
        }
        expireEventIfNeeded(System.currentTimeMillis());
    }

    private void renderWaypointImage(EventRender2D e, Vec3d pos, Waypoint waypoint, double distance) {
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d directionToWaypoint = pos.subtract(cameraPos).normalize();
        org.joml.Vector3fc cameraDirectionF = mc.gameRenderer.getCamera().getHorizontalPlane();
        Vec3d cameraDirection = new Vec3d(cameraDirectionF.x(), cameraDirectionF.y(), cameraDirectionF.z()); 
        double dotProduct = directionToWaypoint.dotProduct(cameraDirection);
        if (dotProduct <= 0.1) return;
        Vec3d screenPos = WorldUtils.getPosition(pos);

        if (screenPos.z <= 0) return;

        if (screenPos.getX() < -100 || screenPos.getX() > mc.getWindow().getScaledWidth() + 100) return;
        if (screenPos.getY() < -100 || screenPos.getY() > mc.getWindow().getScaledHeight() + 100) return;

        float screenX = (float) screenPos.getX();
        float screenY = (float) screenPos.getY();

        Color globalColor = ColorUtils.getGlobalColor();

        float alpha = Math.max(0.4f, 1.0f - (float)(distance / 3000.0));
        Color waypointColor = new Color(globalColor.getRed(), globalColor.getGreen(), globalColor.getBlue(), (int)(255 * alpha));

        float imageSize = 32f;

        try (DepthRenderScope ignored = DepthRenderScope.lequal()) {
            e.getContext().getMatrices().pushMatrix();

            e.getContext().getMatrices().translate(screenX, screenY);

            Render2D.drawTexture(
                    e.getContext().getMatrices(),
                    -imageSize / 2f, -imageSize / 2f,
                    imageSize, imageSize,
                    0f,
                    WAY_TEXTURE,
                    waypointColor
            );

            e.getContext().getMatrices().popMatrix();
        }

        renderWaypointText(e, new Vec3d(screenX, screenY, screenPos.z), waypoint, distance, alpha);
    }


    private void renderWaypointText(EventRender2D e, Vec3d screenPos, Waypoint waypoint, double distance, float alpha) {
        String nameText = waypoint.getName();
        String distanceText = MathUtils.round((float)distance) + "m";

        Color textColor = new Color(255, 255, 255, (int)(255 * alpha));
        Color distanceColor = new Color(200, 200, 200, (int)(255 * alpha * 0.9f));

        float nameWidth = Fonts.ROUNDBOLD.getWidth(nameText, 8f);
        float distanceWidth = Fonts.ROUNDBOLD.getWidth(distanceText, 7f);

        
        float maxWidth = Math.max(nameWidth, distanceWidth);
        float backgroundWidth = maxWidth + 12f;
        float backgroundHeight = 30f;

        float backgroundX = (float)screenPos.getX() - backgroundWidth / 2f;
        float backgroundY = (float)screenPos.getY() + 20f; 
        float[] clampedRect = clampMarkerTextRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);
        backgroundX = clampedRect[0];
        backgroundY = clampedRect[1];

        
        Color backgroundColor = new Color(0, 0, 0, (int)(140 * alpha));
        Render2D.drawRoundedRect(
                e.getContext().getMatrices(),
                backgroundX,
                backgroundY,
                backgroundWidth,
                backgroundHeight,
                3f,
                backgroundColor
        );

        
        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(8f),
                nameText,
                backgroundX + (backgroundWidth - nameWidth) / 2f,
                backgroundY + 5f,
                textColor
        );

        
        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(7f),
                distanceText,
                backgroundX + (backgroundWidth - distanceWidth) / 2f,
                backgroundY + 18f,
                distanceColor
        );
    }

    private float[] clampMarkerTextRect(float x, float y, float width, float height) {
        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();

        float rightReserved = MARKER_TEXT_EDGE_MARGIN;
        if (mc.world != null
                && mc.world.getScoreboard() != null
                && mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) != null) {
            rightReserved = MARKER_TEXT_SCOREBOARD_MARGIN;
        }

        float minX = MARKER_TEXT_EDGE_MARGIN;
        float maxX = Math.max(minX, screenWidth - rightReserved - width);
        float minY = MARKER_TEXT_TOP_MARGIN;
        float maxY = Math.max(minY, screenHeight - MARKER_TEXT_BOTTOM_MARGIN - height);

        float clampedX = Math.max(minX, Math.min(x, maxX));
        float clampedY = Math.max(minY, Math.min(y, maxY));
        return new float[]{clampedX, clampedY};
    }

    public void captureEventCoords(String message) {
        if (message == null || message.isBlank()) return;
        String lowered = message.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        String candidateName = findEventNameCandidate(message, lowered);
        if (candidateName != null) {
            lastEventNameCandidate = candidateName;
            lastEventNameCandidateAtMs = now;
            if (lastEventPosition != null && now - lastEventAtMs <= EVENT_MARKER_TTL_MS) {
                lastEventName = candidateName;
            }
        }
        if (!containsEventToken(lowered)) return;
        if (looksLikeCountdownMessage(lowered)) return;
        // Личные координаты игрока (РТП/телепорт/"/coords") — это НЕ объявление ивента, метку не ставим.
        if (looksLikeSelfCoordsMessage(lowered)) return;

        Vec3d coords = parseEventCoords(message, lowered);
        if (coords == null) return;
        if (lastEventPosition != null
                && now - lastEventAtMs <= EVENT_NAME_WINDOW_MS
                && isWeakerCoordCandidate(message, lowered, coords)) {
            return;
        }
        lastEventPosition = coords;
        lastEventAtMs = now;
        eventWorldKey = mc.world != null ? mc.world.getRegistryKey() : null;
        lastEventFt = containsFtEventToken(lowered);
        String resolvedName = resolveEventName(now, candidateName);
        if (resolvedName != null) {
            lastEventName = resolvedName;
            lastEventNameCandidate = null;
            lastEventNameCandidateAtMs = 0L;
        } else {
            lastEventName = null;
        }
    }

    public boolean placeEventWaypoint() {
        long now = System.currentTimeMillis();
        expireEventIfNeeded(now);
        if (!isEventActive(now)) {
            ChatUtils.sendMessage(I18n.translate("elements.event_marker.missing"));
            return false;
        }
        // Ставим метку только в том измерении, где пойман ивент (иначе координаты не из этого мира).
        if (eventWorldKey != null && mc.world != null && !eventWorldKey.equals(mc.world.getRegistryKey())) {
            return false;
        }
        String name = buildEventWaypointName();
        Waypoint waypoint = new Waypoint(name, lastEventPosition.x, lastEventPosition.y, lastEventPosition.z);
        removeEventWaypoint();
        add(waypoint);
        eventWaypoint = waypoint;
        eventWaypointExpiresAtMs = lastEventAtMs + EVENT_MARKER_TTL_MS;
        ChatUtils.sendMessage(I18n.translate(
                "elements.event_marker.placed",
                name,
                (int) lastEventPosition.x,
                (int) lastEventPosition.y,
                (int) lastEventPosition.z
        ));
        return true;
    }

    public boolean hasEventMarker() {
        long now = System.currentTimeMillis();
        expireEventIfNeeded(now);
        return isEventActive(now);
    }

    public boolean hasPendingEventWaypoint() {
        long now = System.currentTimeMillis();
        expireEventIfNeeded(now);
        return isEventActive(now) && eventWaypoint == null;
    }

    public boolean hasPendingFtEventWaypoint() {
        long now = System.currentTimeMillis();
        expireEventIfNeeded(now);
        return isEventActive(now) && lastEventFt && eventWaypoint == null;
    }

    private void expireEventIfNeeded(long now) {
        if (eventWaypoint != null && !waypoints.contains(eventWaypoint)) {
            eventWaypoint = null;
            eventWaypointExpiresAtMs = 0L;
        }
        if (lastEventNameCandidate != null && now - lastEventNameCandidateAtMs > EVENT_NAME_WINDOW_MS) {
            lastEventNameCandidate = null;
            lastEventNameCandidateAtMs = 0L;
        }
        if (eventWaypointExpiresAtMs > 0L && now >= eventWaypointExpiresAtMs) {
            clearEventState();
            return;
        }
        if (lastEventPosition != null && lastEventAtMs > 0L && now - lastEventAtMs > EVENT_MARKER_TTL_MS) {
            clearEventState();
        }
    }

    private void clearEventState() {
        removeEventWaypoint();
        lastEventPosition = null;
        lastEventName = null;
        lastEventAtMs = 0L;
        eventWorldKey = null;
        lastEventFt = false;
        lastEventNameCandidate = null;
        lastEventNameCandidateAtMs = 0L;
    }

    private void removeEventWaypoint() {
        if (eventWaypoint != null && waypoints.contains(eventWaypoint)) {
            remove(eventWaypoint);
        }
        eventWaypoint = null;
        eventWaypointExpiresAtMs = 0L;
    }

    private boolean isEventActive(long now) {
        if (lastEventPosition == null || lastEventAtMs <= 0L) return false;
        return now - lastEventAtMs <= EVENT_MARKER_TTL_MS;
    }

    private String buildEventWaypointName() {
        if (lastEventName != null && !lastEventName.isBlank()) {
            return lastEventName.trim();
        }
        String fallback = I18n.translate("elements.event_marker.waypoint");
        if (fallback == null || fallback.isBlank()) {
            return "Event";
        }
        return fallback;
    }

    private String resolveEventName(long now, String candidateFromLine) {
        if (candidateFromLine != null && !candidateFromLine.isBlank()) {
            return candidateFromLine.trim();
        }
        if (lastEventNameCandidate != null
                && !lastEventNameCandidate.isBlank()
                && now - lastEventNameCandidateAtMs <= EVENT_NAME_WINDOW_MS) {
            return lastEventNameCandidate.trim();
        }
        return null;
    }

    private String findEventNameCandidate(String message, String lowered) {
        String candidate = extractBracketCandidate(message, lowered);
        if (candidate != null) {
            return candidate;
        }
        return extractPipesCandidate(message, lowered);
    }

    private String extractBracketCandidate(String message, String lowered) {
        Matcher matcher = EVENT_NAME_PATTERN.matcher(message);
        while (matcher.find()) {
            String candidate = normalizeEventName(matcher.group(1));
            if (candidate.isEmpty()) continue;
            if (!containsLetters(candidate)) continue;
            if (isLikelyCoordsCandidate(candidate)) continue;
            String candidateLower = candidate.toLowerCase(Locale.ROOT);
            if (looksLikeEventBox(message) || containsEventToken(lowered) || containsEventToken(candidateLower)) {
                return candidate;
            }
        }
        return null;
    }

    private String extractPipesCandidate(String message, String lowered) {
        Matcher matcher = EVENT_NAME_PIPES_PATTERN.matcher(message);
        while (matcher.find()) {
            String candidate = normalizeEventName(matcher.group(1));
            if (candidate.isEmpty()) continue;
            if (!containsLetters(candidate)) continue;
            if (isLikelyCoordsCandidate(candidate)) continue;
            String candidateLower = candidate.toLowerCase(Locale.ROOT);
            if (looksLikeEventBox(message) || containsEventToken(lowered) || containsEventToken(candidateLower)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeEventName(String candidate) {
        if (candidate == null) return "";
        String trimmed = candidate.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean looksLikeEventBox(String message) {
        return message.contains("|||")
                || message.indexOf('\u2551') >= 0
                || message.indexOf('\u2554') >= 0
                || message.indexOf('\u2557') >= 0
                || message.indexOf('\u255A') >= 0
                || message.indexOf('\u255D') >= 0
                || message.indexOf('\u2550') >= 0;
    }

    private boolean containsLetters(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyCoordsCandidate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char chr = value.charAt(i);
            if (Character.isDigit(chr)
                    || Character.isWhitespace(chr)
                    || chr == '-'
                    || chr == ','
                    || chr == ';'
                    || chr == '|'
                    || chr == '/') {
                continue;
            }
            return false;
        }
        return true;
    }

    private Vec3d parseEventCoords(String message, String lowered) {
        Matcher labeled = EVENT_LABELED_PATTERN.matcher(message);
        if (labeled.find()) {
            return toVec3d(labeled.group(1), labeled.group(2), labeled.group(3));
        }
        Matcher xzLabeled = EVENT_XZ_LABELED_PATTERN.matcher(message);
        if (xzLabeled.find()) {
            return toVec3d(xzLabeled.group(1), currentPlayerY(), xzLabeled.group(2));
        }
        Matcher keyword = EVENT_COORDS_AFTER_KEYWORD_PATTERN.matcher(message);
        while (keyword.find()) {
            Vec3d coords = keyword.group(3) == null
                    ? toVec3d(keyword.group(1), currentPlayerY(), keyword.group(2))
                    : toVec3d(keyword.group(1), keyword.group(2), keyword.group(3));
            if (isPlausibleEventCoords(coords)) {
                return coords;
            }
        }

        if (!allowsLooseCoordParsing(message, lowered)) {
            return null;
        }

        Matcher generic = EVENT_GENERIC_PATTERN.matcher(message);
        while (generic.find()) {
            Vec3d coords = toVec3d(generic.group(1), generic.group(2), generic.group(3));
            if (isPlausibleEventCoords(coords) && !looksLikeTimerCoords(coords)) {
                return coords;
            }
        }

        List<Integer> numbers = new ArrayList<>();
        Matcher numberMatcher = NUMBER_PATTERN.matcher(message);
        while (numberMatcher.find()) {
            try {
                numbers.add(Integer.parseInt(numberMatcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (numbers.size() >= 3) {
            for (int i = numbers.size() - 3; i >= 0; i--) {
                Vec3d coords = new Vec3d(numbers.get(i), numbers.get(i + 1), numbers.get(i + 2));
                if (isPlausibleEventCoords(coords) && !looksLikeTimerCoords(coords)) {
                    return coords;
                }
            }
        }
        if (numbers.size() == 2) {
            Vec3d coords = new Vec3d(numbers.get(0), currentPlayerYValue(), numbers.get(1));
            return isPlausibleEventCoords(coords) && !looksLikeTimerCoords(coords) ? coords : null;
        }
        return null;
    }

    private boolean allowsLooseCoordParsing(String message, String lowered) {
        if (containsCoordinateKeyword(lowered) || looksLikeEventBox(message)) {
            return true;
        }
        return EVENT_LABELED_PATTERN.matcher(message).find()
                || EVENT_XZ_LABELED_PATTERN.matcher(message).find();
    }

    private boolean isWeakerCoordCandidate(String message, String lowered, Vec3d coords) {
        if (containsCoordinateKeyword(lowered) || looksLikeEventBox(message)) {
            return false;
        }
        return looksLikeTimerCoords(coords);
    }

    private boolean looksLikeCountdownMessage(String lowered) {
        return lowered.contains("\u0434\u043e \u0441\u043b\u0435\u0434\u0443\u044e\u0449")
                || lowered.contains("\u043c\u0438\u043d")
                || lowered.contains("\u0441\u0435\u043a")
                || lowered.contains(" minute")
                || lowered.contains(" min ")
                || lowered.contains(" sec")
                || lowered.contains(" second");
    }

    /**
     * Сообщения с ЛИЧНЫМИ координатами игрока (после РТП/телепорта, ответ на «/coords», «вы находитесь…»).
     * В них есть слово «координаты», поэтому раньше они ловились как ивент — это и есть баг (метка ставилась
     * на телепорт). Такие сообщения ивентом НЕ считаем.
     */
    private boolean looksLikeSelfCoordsMessage(String lowered) {
        return lowered.contains("ваши координат")   // "ваши координат…"
                || lowered.contains("твои координат") // "твои координат…"
                || lowered.contains("текущие координат") // "текущие координат…"
                || lowered.contains("телепортирован")        // "телепортирован…"
                || lowered.contains("вы находитесь")               // "вы находитесь…"
                || lowered.contains("your coordinat")
                || lowered.contains("your coord")
                || lowered.contains("you are at")
                || lowered.contains("teleported");
    }

    private boolean looksLikeTimerCoords(Vec3d coords) {
        if (coords == null) return true;
        double max = Math.max(Math.abs(coords.x), Math.max(Math.abs(coords.y), Math.abs(coords.z)));
        double min = Math.min(Math.abs(coords.x), Math.min(Math.abs(coords.y), Math.abs(coords.z)));
        return max <= 120 && min <= 10;
    }

    private boolean containsCoordinateKeyword(String lowered) {
        for (String token : COORD_KEYWORD_TOKENS) {
            if (lowered.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String currentPlayerY() {
        return String.valueOf((int) Math.round(currentPlayerYValue()));
    }

    private double currentPlayerYValue() {
        return mc.player != null ? mc.player.getY() : 0.0;
    }

    private Vec3d toVec3d(String x, String y, String z) {
        try {
            Vec3d coords = new Vec3d(Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z));
            return isPlausibleEventCoords(coords) ? coords : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isPlausibleEventCoords(Vec3d coords) {
        if (coords == null) return false;
        int bottom = mc.world != null ? mc.world.getBottomY() - 16 : -128;
        int top = mc.world != null ? mc.world.getBottomY() + mc.world.getHeight() + 64 : 512;
        return coords.y >= bottom
                && coords.y <= top
                && Math.abs(coords.x) <= 30_000_000
                && Math.abs(coords.z) <= 30_000_000;
    }

    private boolean containsEventToken(String lowered) {
        for (String token : EVENT_TOKENS) {
            if (lowered.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsFtEventToken(String lowered) {
        for (String token : FT_EVENT_TOKENS) {
            if (lowered.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
