package ie.davidmoloney.evrythng;

import com.evrythng.java.wrapper.ApiManager;
import com.evrythng.java.wrapper.core.EvrythngApiBuilder;
import com.evrythng.java.wrapper.exception.EvrythngException;
import com.evrythng.java.wrapper.service.ThngService;
import com.evrythng.thng.resource.model.store.Property;
import com.evrythng.thng.resource.model.store.Thng;

import java.util.List;

public class EvrythngManager {

    protected ThngService thngService;

    public EvrythngManager(final String apiKey) {
        ApiManager apiManager = new ApiManager(apiKey);
        thngService = apiManager.thngService();
    }

    public String createThng(String name) throws EvrythngException {
        Thng localCopy = new Thng();
        localCopy.setName(name);
        return createThng(localCopy);
    }

    private String createThng(final Thng localCopy) throws EvrythngException {
        EvrythngApiBuilder.Builder<Thng> thngBuilder = thngService.thngCreator(localCopy);
        return thngBuilder.execute().getId();
    }

    public Thng retrieveThng(final String id) {
        try {
            EvrythngApiBuilder.Builder<Thng> thngBuilder = thngService.thngReader(id);

            return thngBuilder.execute();
        } catch (EvrythngException e) {
            throw new RuntimeException("Failed to retrieve things", e);
        }
    }


    public void deleteTng(final String id) {
        try {
            EvrythngApiBuilder.Builder<Boolean> thngDeleter = thngService.thngDeleter(id);
            boolean success = thngDeleter.execute();
            if (!success) {
                throw new RuntimeException(String.format("Failed to delete thing with ID: %s", id));
            }
        } catch (EvrythngException e) {
            throw new RuntimeException(String.format("Failed to delete thing with ID: %s", id), e);
        }
    }

    public void setProperty(final String thngId, final String key, final String value, final long timestamp) {
        try {
            thngService.propertyUpdater(thngId, key, value, timestamp).execute();
        } catch (EvrythngException e) {
            throw new RuntimeException(String.format("Problem setting property {key: '%s', value: '%s'}", key, value), e);
        }
    }

    public List<Property> getPropertiesOfThng(final String thngId, final String propertyId) {
        try {
            return thngService.propertyReader(thngId, propertyId).execute();
        } catch (EvrythngException e) {
            throw new RuntimeException("Cannot retrieve properties", e);
        }
    }
}
