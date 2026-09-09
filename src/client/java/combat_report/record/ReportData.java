package combat_report.record;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a recording produced, and nothing about Minecraft.
 *
 * <p>No class in this package imports a game type. That is what lets the whole
 * report pipeline — statistics, HTML, the saved-file reader — run headlessly from
 * {@code ./gradlew reportPreview}, so the numbers and the page can be checked
 * without launching the game.
 *
 * <p>Fields are public and plain because Gson maps them straight to the JSON that
 * is written beside the report.
 */
public final class ReportData {

	public String modVersion = "";
	public String minecraftVersion = "";

	public String playerName = "";
	public String playerUuid = "";

	public long startEpochMs;
	public long endEpochMs;
	public String startUtc = "";
	public String endUtc = "";

	/** Average ping over the recording. Context only; it adjusts no measurement. */
	public double pingMs;

	/** How many separate fights the recording was split into. */
	public int fights;

	/**
	 * Time spent in those fights, in milliseconds. Everything between fights is
	 * excluded, so this is smaller — usually much smaller — than end minus start.
	 */
	public long fightMs;

	/** Display names of everyone fought, in first-seen order. Indexed by events. */
	public List<String> opponents = new ArrayList<>();

	/**
	 * False when no opponent health change was ever observed, which happens on
	 * servers that do not sync other players' health. Damage-based figures are
	 * withheld rather than printed as zero when this is false.
	 */
	public boolean opponentHealthSeen;

	/**
	 * Attacks thrown with a piercing weapon (the 1.21.11 spears), which are counted
	 * but not measured. See {@code FightWatcher.onSwingBegin} for why.
	 */
	public int piercingSwings;

	public List<Swing> swings = new ArrayList<>();
	public List<Combo> combosDealt = new ArrayList<>();
	public List<Combo> combosTaken = new ArrayList<>();
	public List<Jump> jumps = new ArrayList<>();
	public List<Trade> trades = new ArrayList<>();

	/** Filled in by {@link ReportStats#summarise} at stop time. */
	public Summary summary = new Summary();

	/** One attack thrown at an opponent, hit or miss. */
	public static final class Swing {
		public long t;
		public boolean landed;
		/** Eye to the nearest point of the target's hitbox, in blocks. */
		public double reach;
		/** {@code HitType.name()} for a landed hit, null for a miss. */
		public String type;
		/** Attack-cooldown charge at the moment of the swing, 0..1. */
		public double charge;
		public int opponent = -1;
	}

	/** A run of hits on one opponent with no gap longer than the combo window. */
	public static final class Combo {
		public long start;
		public long end;
		public int hits;
		public int opponent = -1;
	}

	/** One jump taken during a fight. */
	public static final class Jump {
		public long t;
		/** True if this jump was close enough to a hit taken to be a reset attempt. */
		public boolean attempt;
		/** Milliseconds from the hit to the jump. Negative means the jump came first. */
		public long deltaMs;
		/** True if the attempt landed inside the success window. */
		public boolean reset;
		/** True if the opponent hit you before you touched the ground again. */
		public boolean deflected;
	}

	/** A hit dealt and a hit taken close enough together to be one exchange. */
	public static final class Trade {
		public long t;
		public double dealt;
		public double taken;
		/**
		 * Velocity along the you-to-opponent axis shortly after the exchange, as a
		 * percentage of sprint speed. Positive is toward them.
		 */
		public double momentumPct;
		/** False if the sample never ran (the fight ended, or they went out of view). */
		public boolean momentumKnown;
		/**
		 * False when no drop in the opponent's health was ever observed for this
		 * exchange. Such a trade is left out of the win rate rather than scored as a
		 * loss: a hit that did nothing and a hit whose result never reached this
		 * client look exactly the same from here.
		 */
		public boolean damageKnown;
		public int opponent = -1;
	}

	/** Everything the report prints, derived from the lists above. */
	public static final class Summary {

		// Section 1: hits
		public int swings;
		public int landed;
		public int missed;
		public double accuracyPct;

		public int pick;
		public int kb;
		public int crit;
		public int sweep;
		public int plain;
		public double pickPct;
		public double kbPct;
		public double critPct;
		public double sweepPct;
		public double plainPct;
		/** Average charge of the uncharged hits, 0..1. Says how early they were. */
		public double avgPickCharge;

		public double avgReach;
		public double rangeLo;
		public double rangeHi;
		public double maxReach;
		public int threeBlockHits;
		public double threeBlockPct;

		// Section 2: combos
		public int combos;
		public int comboHits;
		public double avgComboHits;
		public int singleHits;

		public int combosTaken;
		public int comboHitsTaken;
		public double avgComboHitsTaken;
		public int singleHitsTaken;

		public double comboFrequencyMs;
		public int comboGaps;

		// Section 3: jumps
		public int jumps;
		public int resetAttempts;
		public int resets;
		public double resetPct;
		public double avgResetDeltaMs;
		public int deflected;
		public double deflectedPct;

		// Section 4: momentum and trades
		public int trades;
		public int tradesWithMomentum;
		public double avgMomentumPct;
		public int forwardTrades;
		public double forwardPct;

		public boolean damageKnown;
		public int damageWins;
		public int damageLosses;
		public int damageDraws;
		public double damageWinPct;
	}
}
