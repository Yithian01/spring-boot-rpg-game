package com.example.demo.domain.save;

import com.example.demo.domain.enums.LocationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameStatus {
    private LocationType location;
    private Integer dungeonId; // TOWN이면 null
}
