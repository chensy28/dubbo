package com.alibaba.dubbo.governance.service.impl;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.governance.service.OverrideService;
import com.alibaba.dubbo.governance.service.OwnerService;
import com.alibaba.dubbo.governance.service.ProviderService;
import com.alibaba.dubbo.registry.common.domain.Override;
import com.alibaba.dubbo.registry.common.domain.Owner;
import com.alibaba.dubbo.registry.common.domain.Provider;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OwnerServiceImpl extends AbstractService implements OwnerService {

    @Autowired
    ProviderService providerService;

    @Autowired
    OverrideService overrideService;

    public List<String> findAllServiceNames() {
        // @system method stub
        return null;
    }

    public List<String> findServiceNamesByUsername(String username) {
        // @system method stub
        return null;
    }

    public List<String> findUsernamesByServiceName(String serviceName) {
        // @system method stub
        return null;
    }

    public List<Owner> findByService(String serviceName) {
        List<Provider> pList = providerService.findByService(serviceName);
        List<Override> cList = overrideService.findByServiceAndAddress(serviceName, Constants.ANYHOST_VALUE);
        return toOverrideLiset(pList, cList);
    }

    public List<Owner> findAll() {
        List<Provider> pList = providerService.findAll();
        List<Override> cList = overrideService.findAll();
        return toOverrideLiset(pList, cList);
    }

    public Owner findById(Long id) {

        return null;
    }

    private List<Owner> toOverrideLiset(List<Provider> pList, List<Override> cList) {
        Map<String, Owner> oList = new HashMap<String, Owner>();
        for (Provider p : pList) {
            if (p.getUsername() != null) {
                for (String username : Constants.COMMA_SPLIT_PATTERN.split(p.getUsername())) {
                    Owner o = new Owner();
                    o.setService(p.getService());
                    o.setUsername(username);
                    oList.put(o.getService() + "/" + o.getUsername(), o);
                }
            }
        }
        for (Override c : cList) {
            Map<String, String> params = StringUtils.parseQueryString(c.getParams());
            String usernames = params.get("owner");
            if (usernames != null && usernames.length() > 0) {
                for (String username : Constants.COMMA_SPLIT_PATTERN.split(usernames)) {
                    Owner o = new Owner();
                    o.setService(c.getService());
                    o.setUsername(username);
                    oList.put(o.getService() + "/" + o.getUsername(), o);
                }
            }
        }
        return new ArrayList<Owner>(oList.values());
    }

    public void saveOwner(Owner owner) {
        List<Override> overrides = overrideService.findByServiceAndAddress(owner.getService(), Constants.ANYHOST_VALUE);
        if (overrides == null || overrides.size() == 0) {
            Override override = new Override();
            override.setAddress(Constants.ANYHOST_VALUE);
            override.setService(owner.getService());
            override.setEnabled(true);
            override.setParams("owner=" + owner.getUsername());
            overrideService.saveOverride(override);
        } else {
            for (Override override : overrides) {
                Map<String, String> params = StringUtils.parseQueryString(override.getParams());
                String usernames = params.get("owner");
                if (usernames == null || usernames.length() == 0) {
                    usernames = owner.getUsername();
                } else {
                    usernames = usernames + "," + owner.getUsername();
                }
                params.put("owner", usernames);
                override.setParams(StringUtils.toQueryString(params));
                overrideService.updateOverride(override);
            }
        }
    }

    public void deleteOwner(Owner owner) {
        List<Override> overrides = overrideService.findByServiceAndAddress(owner.getService(), Constants.ANYHOST_VALUE);
        if (overrides == null || overrides.size() == 0) {
            Override override = new Override();
            override.setAddress(Constants.ANYHOST_VALUE);
            override.setService(owner.getService());
            override.setEnabled(true);
            override.setParams("owner=" + owner.getUsername());
            overrideService.saveOverride(override);
        } else {
            for (Override override : overrides) {
                Map<String, String> params = StringUtils.parseQueryString(override.getParams());
                String usernames = params.get("owner");
                if (usernames != null && usernames.length() > 0) {
                    if (usernames.equals(owner.getUsername())) {
                        params.remove("owner");
                    } else {
                        usernames = usernames.replace(owner.getUsername() + ",", "").replace("," + owner.getUsername(), "");
                        params.put("owner", usernames);
                    }
                    if (params.size() > 0) {
                        override.setParams(StringUtils.toQueryString(params));
                        overrideService.updateOverride(override);
                    } else {
                        overrideService.deleteOverride(override.getId());
                    }
                }
            }
        }
    }

}
