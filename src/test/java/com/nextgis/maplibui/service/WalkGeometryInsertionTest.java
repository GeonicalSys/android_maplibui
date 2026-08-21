package com.nextgis.maplibui.service;

import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class WalkGeometryInsertionTest {
    @Test
    public void consecutiveWalkPointsStayBetweenSelectedVertexAndItsFormerNextVertex() {
        GeoPoint selected = new GeoPoint(0, 0);
        GeoPoint formerNext = new GeoPoint(10, 0);
        GeoPoint walkA = new GeoPoint(2, 0);
        GeoPoint walkB = new GeoPoint(4, 0);
        GeoLineString line = new GeoLineString();
        line.add(selected);
        line.add(formerNext);

        int next = WalkGeometryInsertion.insert(line, 1, walkA);
        next = WalkGeometryInsertion.insert(line, next, walkB);

        assertEquals(3, next);
        assertEquals(4, line.getPointCount());
        assertSame(selected, line.getPoint(0));
        assertSame(walkA, line.getPoint(1));
        assertSame(walkB, line.getPoint(2));
        assertSame(formerNext, line.getPoint(3));
    }
}
