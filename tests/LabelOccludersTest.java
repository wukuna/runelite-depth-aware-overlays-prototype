package net.runelite.client.plugins.gpu;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabelOccludersTest
{
    @Test public void commonPropsAndUnknownLandscapes()
    {
        assertFalse(LabelOccluders.defaultBlocks("Open chest"));
        assertFalse(LabelOccluders.defaultBlocks("Crates"));
        assertFalse(LabelOccluders.defaultBlocks("Oak wardrobe"));
        assertTrue(LabelOccluders.defaultBlocks("Cave wall"));
        assertTrue(LabelOccluders.defaultBlocks("Rock formation"));
        assertTrue(LabelOccluders.defaultBlocks("Door"));
        assertTrue(LabelOccluders.defaultBlocks("null"));
        assertTrue(LabelOccluders.defaultBlocks(null));
    }
    @Test public void overridesAndConflicts()
    {
        LabelOccluders classifier = new LabelOccluders();
        classifier.configure("100, 101", "100, 102 nonsense -1");
        assertTrue(classifier.blocks(100));
        assertTrue(classifier.blocks(101));
        assertFalse(classifier.blocks(102));
        assertTrue(classifier.blocks(103));
        assertTrue(classifier.blocks(LabelOccluders.PLAYER));
        assertTrue(classifier.blocks(LabelOccluders.TERRAIN));
        assertFalse(classifier.blocks(LabelOccluders.IGNORE));
        classifier.configure("", "");
        assertTrue(classifier.blocks(102));
    }
}
