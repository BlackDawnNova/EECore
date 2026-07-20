package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectPoolTest {

    @Test
    void background_sruListReuse() {
        var bg = new ObjectPool.Background(100);
        List<ShardResultUnit> a = bg.acquireSruList();
        bg.releaseSruList(a);
        List<ShardResultUnit> b = bg.acquireSruList();
        assertSame(a, b);
    }

    @Test
    void background_aggMapReuse() {
        var bg = new ObjectPool.Background(100);
        var a = bg.acquireAggMap();
        bg.releaseAggMap(a);
        var b = bg.acquireAggMap();
        assertSame(a, b);
    }

    @Test
    void background_clearOnRelease() {
        var bg = new ObjectPool.Background(100);
        List<ShardResultUnit> list = bg.acquireSruList();
        list.add(new ShardResultUnit(1, 1, 1, 1, 1, new long[0], new long[0], 1.0, 100, 100));
        bg.releaseSruList(list);
        assertTrue(bg.acquireSruList().isEmpty());
    }

    @Test
    void background_createsNewWhenEmpty() {
        var bg = new ObjectPool.Background(100);
        List<ShardResultUnit> a = bg.acquireSruList();
        List<ShardResultUnit> b = bg.acquireSruList();
        assertNotNull(a); assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    void background_releaseNullSafe() {
        var bg = new ObjectPool.Background(100);
        bg.releaseSruList(null);
        bg.releaseAggMap(null);
    }
}
