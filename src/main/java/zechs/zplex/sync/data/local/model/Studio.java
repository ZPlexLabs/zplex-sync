package zechs.zplex.sync.data.local.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "Studio")
@Table(name = "studios")
public class Studio {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "logo_path", length = Integer.MAX_VALUE)
    private String logoPath;

    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "origin_country", nullable = false, length = Integer.MAX_VALUE)
    private String originCountry;

}