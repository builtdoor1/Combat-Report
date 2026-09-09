package combat_report.combat;

/**
 * How a landed hit was thrown.
 *
 * <p>These five are mutually exclusive and they cover every landed hit, which is
 * what lets the report print them as a spread that sums to 100%. That is not a
 * choice made here — it falls out of vanilla's own logic in {@code Player.attack},
 * which decides sprint, crit and sweep in that order and lets at most one win. See
 * {@link HitClassifier} for the conditions, which are copied from it verbatim.
 */
public enum HitType {

	/** Swung before the cooldown finished. Vanilla's {@code strong} flag was false. */
	PICK("Pick", "Swung before the attack cooldown finished, so the hit did reduced damage and no sweep, crit or knockback bonus."),

	/** Sprint hit: full knockback, the w-tap hit. */
	KB("KB", "Landed while sprinting, so it carried the extra knockback."),

	/** Critical hit: falling, not sprinting, charged. */
	CRIT("Crit", "Landed while falling and not sprinting, for 1.5x damage."),

	/** Sweep attack: charged, grounded, near-stationary, sword in hand. */
	SWEEP("Sweep", "A charged sword swing thrown standing still on the ground, which sweeps instead of hitting for full."),

	/** Charged, but none of the above — an axe hit, or a sword swung while walking. */
	PLAIN("Plain", "Fully charged, but not a sprint hit, crit or sweep - typically an axe, or a sword swung while moving.");

	private final String label;
	private final String explanation;

	HitType(String label, String explanation) {
		this.label = label;
		this.explanation = explanation;
	}

	public String label() {
		return this.label;
	}

	public String explanation() {
		return this.explanation;
	}
}
