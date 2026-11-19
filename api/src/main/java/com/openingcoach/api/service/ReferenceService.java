package com.openingcoach.api.service;

import com.openingcoach.api.CoachProperties;
import com.openingcoach.core.Stats;
import com.openingcoach.core.reference.ReferenceBook;
import com.openingcoach.core.reference.ReferenceIndex;
import com.openingcoach.core.reference.ReferenceIndexBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Owns the reference book. On first start it builds the disk index (sorted files + Bloom
 * filter) from the reference PGN; afterwards it just opens the files.
 */
@Service
public class ReferenceService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceService.class);

    private final ReferenceIndex index;
    private final String unavailableReason;

    public ReferenceService(CoachProperties props) {
        Path dataDir = resolveDataDir(props.dataDir());
        Path indexDir = dataDir.resolve("index");
        ReferenceIndex opened = null;
        String reason = null;
        try {
            if (!ReferenceIndex.exists(indexDir)) {
                Path pgn = dataDir.resolve(props.referencePgn());
                if (!Files.exists(pgn)) {
                    throw new IOException("Reference PGN not found at " + pgn.toAbsolutePath()
                            + " - run scripts/fetch-reference.sh");
                }
                log.info("Building reference index from {} (minElo {}, maxPly {})", pgn, props.minElo(), props.maxPly());
                try (Reader r = Files.newBufferedReader(pgn, StandardCharsets.UTF_8)) {
                    var report = ReferenceIndexBuilder.build(r, indexDir, props.minElo(), props.maxPly(), props.bloomFpp());
                    log.info("Reference index built: {}", report);
                }
            }
            opened = ReferenceIndex.open(indexDir);
            log.info("Reference index loaded: {} positions, Bloom filter {} KB", opened.positionCount(),
                    opened.bloom().memoryBytes() / 1024);
        } catch (IOException | RuntimeException e) {
            reason = e.getMessage();
            log.warn("Running without a reference book: {}", reason);
        }
        this.index = opened;
        this.unavailableReason = reason;
    }

    static Path resolveDataDir(String configured) {
        Path p = Path.of(configured);
        if (p.isAbsolute() || Files.isDirectory(p)) {
            return p;
        }
        Path parent = Path.of("..").resolve(configured);
        return Files.isDirectory(parent) ? parent : p;
    }

    /** The book used by analyses; an empty book if the index could not be loaded. */
    public ReferenceBook book() {
        if (index != null) {
            return index;
        }
        return new ReferenceBook() {
            @Override
            public Optional<Stats> position(long key) {
                return Optional.empty();
            }

            @Override
            public List<RefMove> moves(long key) {
                return List.of();
            }
        };
    }

    public Optional<ReferenceIndex> index() {
        return Optional.ofNullable(index);
    }

    public String unavailableReason() {
        return unavailableReason;
    }

    @PreDestroy
    void close() throws IOException {
        if (index != null) {
            index.close();
        }
    }
}
