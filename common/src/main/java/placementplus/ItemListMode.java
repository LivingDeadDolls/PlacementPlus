package placementplus;

/** Which items Fast Placement works with. */
public enum ItemListMode {
    /** Every block. */
    ALL,
    /** Every block except those on the excluded list. */
    EXCLUDE,
    /** Only the blocks on the included list. */
    INCLUDE
}
