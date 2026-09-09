package combat_report.combat;

/**
 * Every tuning number the measurements depend on, in one place.
 *
 * <p>These are deliberately constants rather than settings. A report is only
 * comparable with another report if both were measured the same way, and a value
 * like the combo gap or the trade window does not tune a preference — it defines
 * what the word means. A wrong one quietly changes what the numbers say instead of
 * visibly breaking something.
 */
public final class Constants {

	private Constants() {
	}

	// ---- Fight segmentation -------------------------------------------------

	/**
	 * A fight ends when neither player has landed a hit for this long. Nothing
	 * between fights is measured: walking back across the map is not combat, and
	 * counting it would swamp the time-based figures (combo frequency above all).
	 */
	public static final long FIGHT_IDLE_MS = 8_000L;

	/**
	 * A fight also ends if the opponent gets further away than this. Sixteen blocks
	 * is well past melee range but inside the distance you would chase someone, so
	 * a fight survives a short disengage and does not survive a run for the exit.
	 */
	public static final double FIGHT_MAX_DISTANCE = 16.0;

	// ---- Hits ---------------------------------------------------------------

	/**
	 * A swing counts as aimed at an opponent if a player is within this distance.
	 * Twice vanilla melee range: a swing thrown from too far away is a real miss and
	 * belongs in the accuracy figure, a swing at empty air with nobody near you is
	 * not a miss at anything and is dropped instead.
	 */
	public static final double SWING_TARGET_RANGE = 6.0;

	/** ...and within this angle of the crosshair. */
	public static final double SWING_TARGET_CONE_DEG = 30.0;

	/**
	 * "3 block accuracy" counts landed hits beyond this distance. Named for three
	 * blocks, measured at 2.9, because vanilla's own limit is 3.0 and a hit measured
	 * at exactly the limit is the interesting case, not the excluded one.
	 */
	public static final double THREE_BLOCK_THRESHOLD = 2.9;

	/** Width of the band the average hit range is reported in, e.g. 2.5-2.8. */
	public static final double RANGE_BAND_WIDTH = 0.3;

	// ---- Combos -------------------------------------------------------------

	/**
	 * Two hits belong to the same combo if they land within this gap. Roughly the
	 * sword's full attack-cooldown recharge (~625 ms) plus grace: if your weapon is
	 * charged and you have not swung, the combo is over.
	 */
	public static final long COMBO_GAP_MS = 700L;

	/**
	 * A combo is two or more hits. Isolated single hits are counted separately
	 * rather than averaged in as one-hit combos, which would drag the average toward
	 * 1 and say more about how often you poke than about how well you combo.
	 */
	public static final int COMBO_MIN_HITS = 2;

	// ---- Jumps --------------------------------------------------------------

	/**
	 * A jump this close to a hit taken is scored as a jump-reset attempt. Further
	 * out it is discarded rather than counted as a miss: a jump a fifth of a second
	 * either side of taking damage is just a jump that happened nearby.
	 */
	public static final long JUMP_ATTEMPT_MS = 200L;

	/** A jump this long after the hit, or sooner, cancelled the knockback. */
	public static final long JUMP_SUCCESS_MIN_MS = 0L;

	public static final long JUMP_SUCCESS_MAX_MS = 80L;

	// ---- Trades -------------------------------------------------------------

	/**
	 * Two hits — one dealt, one taken — are a trade if they land within this of each
	 * other. Wide enough to catch a genuine simultaneous exchange at any realistic
	 * ping, narrow enough that the second hit of a combo is not read as a trade with
	 * the first.
	 */
	public static final long TRADE_WINDOW_MS = 400L;

	/**
	 * Momentum is sampled this many ticks after the later hit of a trade: long
	 * enough for the knockback impulse to have been applied and moved you, short
	 * enough that it has not yet decayed into whatever you did next.
	 */
	public static final int MOMENTUM_SAMPLE_TICKS = 3;

	/**
	 * Vanilla sprint speed in blocks per second, used as the 100% reference for
	 * trade momentum. Scaled at runtime by the player's movement-speed attribute so
	 * a Speed effect does not read as superhuman momentum.
	 */
	public static final double SPRINT_SPEED_BPS = 5.612;

	/** The default movement-speed attribute the reference above was measured at. */
	public static final double BASE_MOVEMENT_SPEED = 0.1;

	/**
	 * A health drop this soon AFTER a hit is attributed to that hit, plus the
	 * measured latency. Health arrives in a separate packet from the damage event,
	 * so the two are matched by time - and the drop can only ever arrive after the
	 * click that caused it, which is why the window looks forward and barely back.
	 */
	public static final long DAMAGE_LINK_MS = 500L;

	/** Never widen the link window past this, however bad the connection is. */
	public static final long DAMAGE_LINK_MAX_MS = 900L;

	/** A drop this far BEFORE the hit belongs to the previous hit, not this one. */
	public static final long DAMAGE_LINK_BACK_MS = 100L;

	/**
	 * Damage is read this many ticks after a trade - much later than the momentum
	 * sample, because the opponent's health has to travel to the server and back
	 * before this client can see it. Reading it at the momentum deadline scored
	 * every high-ping trade as a loss, since an unobserved hit and a hit that did
	 * nothing look identical.
	 */
	public static final int DAMAGE_SETTLE_TICKS = 24;

	// ---- Misc ---------------------------------------------------------------

	/** Ticks per second, for converting a per-tick position delta into blocks/s. */
	public static final double TICKS_PER_SECOND = 20.0;

	/** Newest-first cap on the saved-report list shown in game. */
	public static final int MAX_LISTED_REPORTS = 60;
}
