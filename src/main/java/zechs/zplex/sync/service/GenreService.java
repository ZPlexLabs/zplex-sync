package zechs.zplex.sync.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zechs.zplex.sync.data.local.model.Genre;
import zechs.zplex.sync.repository.GenreRepository;

import java.util.List;

@Service
public class GenreService {

    private final GenreRepository genreRepository;

    @Autowired
    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    public void saveGenres(List<Genre> genres) {
        genreRepository.saveAll(genres);
    }

    public List<Genre> getAllGenres() {
        return genreRepository.findAll();
    }
}
