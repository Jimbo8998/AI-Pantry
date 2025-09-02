package com.example.aipantry.services;
import java.util.*;

public class AliasResolver {
    private final Map<String,String> aliasToCanonical = new HashMap<>();
    public AliasResolver(Map<String, List<String>> aliases){
        if (aliases==null) return;
        for (var e: aliases.entrySet()){
            String canon=e.getKey().toLowerCase();
            aliasToCanonical.put(canon, canon);
            for (String a: e.getValue()) aliasToCanonical.put(a.toLowerCase(), canon);
        }
    }
    public String canonical(String name){ return name==null? null : aliasToCanonical.getOrDefault(name.toLowerCase(), name.toLowerCase()); }
}
