package sh.harold.creative.library.entity.house;

public final class HousePresentationFactory {

    private HousePresentationFactory() {
    }

    public static HousePresentation create(HouseServiceSpec spec) {
        HouseValidator.validate(spec);
        return new HousePresentation(spec.name(), spec.role().lineComponent(), spec.promptMode().asComponent());
    }
}
