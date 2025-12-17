package com.mycompany.moduloia.features;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TfidfVectorizerStateTest {

    @Test
    public void testSerializeLoadKeepsTransformConsistent() {
        List<String> docs = Arrays.asList(
                "hola mundo",
                "hola hola oferta",
                "compra ahora oferta",
                "mundo mundo compra"
        );

        TfidfVectorizer v1 = new TfidfVectorizer(50);
        v1.fitTransform(docs);

        byte[] st = v1.serializeState();

        TfidfVectorizer v2 = new TfidfVectorizer(50);
        v2.loadState(st);

        Assert.assertEquals(v1.getFeatureSize(), v2.getFeatureSize());

        String probe = "hola compra oferta";
        double[] a = v1.transformOne(probe);
        double[] b = v2.transformOne(probe);

        Assert.assertEquals(a.length, b.length);

        for (int i = 0; i < a.length; i++) {
            Assert.assertEquals(a[i], b[i], 1e-12);
        }
    }
}
