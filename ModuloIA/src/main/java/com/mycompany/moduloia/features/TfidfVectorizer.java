package com.mycompany.moduloia.features;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TfidfVectorizer implements FeatureExtractor<String> {

    private final int maxVocab;
    private Map<String, Integer> vocabIndex = new HashMap<>();
    private double[] idf;

    public TfidfVectorizer(int maxVocab) {
        this.maxVocab = maxVocab;
    }

    @Override
    public int getFeatureSize() {
        return vocabIndex.size();
    }

    @Override
    public List<double[]> fitTransform(List<String> rawInputs) {
        buildVocab(rawInputs);
        computeIdf(rawInputs);
        List<double[]> out = new ArrayList<>(rawInputs.size());
        for (String s : rawInputs) {
            out.add(transformOne(s));
        }
        return out;
    }

    @Override
    public double[] transformOne(String rawInput) {
        String[] toks = tokenize(rawInput);
        double[] vec = new double[vocabIndex.size()];
        if (toks.length == 0) {
            return vec;
        }

        Map<Integer, Integer> tfCounts = new HashMap<>();
        for (String t : toks) {
            Integer idx = vocabIndex.get(t);
            if (idx != null) {
                tfCounts.put(idx, tfCounts.getOrDefault(idx, 0) + 1);
            }
        }

        int len = toks.length;
        for (Map.Entry<Integer, Integer> e : tfCounts.entrySet()) {
            int idx = e.getKey();
            double tf = (double) e.getValue() / (double) len;
            vec[idx] = tf * idf[idx];
        }
        return vec;
    }

    private void buildVocab(List<String> docs) {
        Map<String, Integer> freq = new HashMap<>();
        for (String d : docs) {
            for (String t : tokenize(d)) {
                if (!t.isEmpty()) {
                    freq.put(t, freq.getOrDefault(t, 0) + 1);
                }
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(freq.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        vocabIndex.clear();
        int limit = Math.min(maxVocab, sorted.size());
        for (int i = 0; i < limit; i++) {
            vocabIndex.put(sorted.get(i).getKey(), i);
        }

        idf = new double[vocabIndex.size()];
    }

    private void computeIdf(List<String> docs) {
        int n = docs.size();
        int[] df = new int[vocabIndex.size()];

        for (String d : docs) {
            Set<Integer> seen = new HashSet<>();
            for (String t : tokenize(d)) {
                Integer idx = vocabIndex.get(t);
                if (idx != null) {
                    seen.add(idx);
                }
            }
            for (Integer idx : seen) {
                df[idx] += 1;
            }
        }

        for (int i = 0; i < df.length; i++) {
            idf[i] = Math.log(((double) (n + 1)) / ((double) (df[i] + 1))) + 1.0;
        }
    }

    private String[] tokenize(String s) {
        if (s == null) {
            return new String[0];
        }
        String cleaned = s.toLowerCase(Locale.ROOT).trim();
        cleaned = cleaned.replaceAll("[^\\p{L}\\p{N}]+", " ");
        if (cleaned.isEmpty()) {
            return new String[0];
        }
        return cleaned.split("\\s+");
    }

    @Override
    public byte[] serializeState() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(maxVocab);
            dos.writeInt(vocabIndex.size());

            String[] tokensByIdx = new String[vocabIndex.size()];
            for (Map.Entry<String, Integer> e : vocabIndex.entrySet()) {
                tokensByIdx[e.getValue()] = e.getKey();
            }

            for (String tok : tokensByIdx) {
                byte[] b = tok.getBytes(StandardCharsets.UTF_8);
                dos.writeInt(b.length);
                dos.write(b);
            }

            dos.writeInt(idf.length);
            for (double v : idf) {
                dos.writeDouble(v);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TFIDF state", e);
        }
    }

    @Override
    public void loadState(byte[] state) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(state));

            int storedMax = dis.readInt();
            // si storedMax != maxVocab, no es fatal, pero indica config distinta

            int size = dis.readInt();
            vocabIndex.clear();

            String[] tokensByIdx = new String[size];
            for (int i = 0; i < size; i++) {
                int len = dis.readInt();
                byte[] b = new byte[len];
                dis.readFully(b);
                tokensByIdx[i] = new String(b, StandardCharsets.UTF_8);
            }
            for (int i = 0; i < tokensByIdx.length; i++) {
                vocabIndex.put(tokensByIdx[i], i);
            }

            int idfLen = dis.readInt();
            idf = new double[idfLen];
            for (int i = 0; i < idfLen; i++) {
                idf[i] = dis.readDouble();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TFIDF state", e);
        }
    }
}
