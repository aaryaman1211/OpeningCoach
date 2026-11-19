package com.openingcoach.api.web;

import com.openingcoach.api.dto.Dtos.ReferenceInfo;
import com.openingcoach.api.service.ReferenceService;
import com.openingcoach.core.reference.ReferenceIndex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** "Under the hood": reference book size, Bloom filter parameters and live lookup counters. */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final ReferenceService reference;

    public ReferenceController(ReferenceService reference) {
        this.reference = reference;
    }

    @GetMapping
    public ReferenceInfo info() {
        return reference.index().map(this::describe)
                .orElseGet(() -> new ReferenceInfo(false, reference.unavailableReason(), null, null, null, null, null));
    }

    private ReferenceInfo describe(ReferenceIndex idx) {
        ReferenceIndex.Metrics m = idx.metrics();
        Map<String, Object> bloom = new LinkedHashMap<>();
        bloom.put("bits", m.bloomBits());
        bloom.put("hashFunctions", m.bloomHashes());
        bloom.put("keys", m.positions());
        bloom.put("bitsPerKey", round((double) m.bloomBits() / Math.max(1, m.positions())));
        bloom.put("memoryBytes", m.bloomBytes());
        // a HashSet<Long> costs roughly 48-64 bytes per entry on a 64-bit JVM (entry + boxed Long + table slot);
        // the benchmark module measures this instead of assuming it
        bloom.put("hashSetEstimateBytes", m.positions() * 56);
        bloom.put("theoreticalFpp", m.bloomExpectedFpp());
        bloom.put("fillRatio", round(m.bloomFillRatio()));
        Map<String, Object> lookups = new LinkedHashMap<>();
        lookups.put("total", m.lookups());
        lookups.put("rejectedByBloom", m.bloomRejections());
        lookups.put("confirmedOnDisk", m.confirmedHits());
        lookups.put("falsePositives", m.falsePositives());
        lookups.put("observedFpp", m.observedFpp());
        lookups.put("diskProbes", m.diskProbes());
        Map<String, String> build = new TreeMap<>();
        m.meta().forEach((k, v) -> build.put(String.valueOf(k), String.valueOf(v)));
        return new ReferenceInfo(true, null, m.positions(), m.moves(), bloom, lookups, build);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
