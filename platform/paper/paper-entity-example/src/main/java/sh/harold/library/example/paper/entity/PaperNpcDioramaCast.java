package sh.harold.library.example.paper.entity;

import org.bukkit.Location;
import sh.harold.library.entity.house.HouseServiceEntity;

record PaperNpcDioramaCast(
        HouseServiceEntity librarian,
        HouseServiceEntity archivist,
        HouseServiceEntity scribe,
        HouseServiceEntity researcher,
        HouseServiceEntity nightClerk,
        HouseServiceEntity blacksmith,
        HouseServiceEntity apprentice,
        HouseServiceEntity quartermaster,
        PaperNpcBehaviorCatalog.AuthoredBehaviors authored,
        Location librarianLocation
) {
}
