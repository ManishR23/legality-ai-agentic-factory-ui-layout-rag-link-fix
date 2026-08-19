package com.example.brdagent;

import com.fasterxml.jackson.databind.JsonNode;

final class JsonUiHelper {
    private JsonUiHelper(){}
    static String text(JsonNode n,String f,String d){ JsonNode v=n==null?null:n.path(f); return v==null||v.isMissingNode()||v.isNull()?d:v.asText(); }
    static int size(JsonNode n){return n!=null&&n.isArray()?n.size():0;}
    static int flightCount(JsonNode s){int c=0; for(JsonNode p:s.path("pairings"))for(JsonNode d:p.path("duties"))for(JsonNode a:d.path("activities"))if("FLIGHT".equalsIgnoreCase(text(a,"type","")))c++; return c;}
    static int nonFlyingCount(JsonNode s){int c=0; for(JsonNode p:s.path("pairings"))for(JsonNode d:p.path("duties"))for(JsonNode a:d.path("activities"))if(!"FLIGHT".equalsIgnoreCase(text(a,"type","")))c++; c+=size(s.path("nonFlyingActivities")); return c;}
    static String scheduleDetail(JsonNode s){
        StringBuilder b=new StringBuilder(); b.append("SCHEDULE\n").append("ID: ").append(text(s,"scheduleId","UNKNOWN")).append("   Crew: ").append(text(s,"crewId","UNKNOWN")).append("   Base: ").append(text(s,"base","UNKNOWN")).append('\n');
        b.append("Period: ").append(text(s,"scheduleStart","?")).append(" to ").append(text(s,"scheduleEnd","?")).append("\nFlights: ").append(flightCount(s)).append("   Non-Flying: ").append(nonFlyingCount(s)).append("\n\n");
        for(JsonNode p:s.path("pairings")){ b.append("PAIRING ").append(text(p,"pairingId","UNKNOWN")).append("  ").append(text(p,"startBase","?")).append(" -> ").append(text(p,"endBase","?")).append("\n");
            for(JsonNode d:p.path("duties")){ b.append("  DUTY ").append(text(d,"dutyId","UNKNOWN")).append("  ").append(text(d,"reportTime","?")).append(" -> ").append(text(d,"releaseTime","?")).append('\n');
                for(JsonNode a:d.path("activities")){ String type=text(a,"type","ACTIVITY"); b.append("    ").append(type).append("  "); if("FLIGHT".equalsIgnoreCase(type)) b.append(text(a,"flightNumber","")).append(' ').append(text(a,"origin","?")).append('-').append(text(a,"destination","?")).append("  ").append(text(a,"departure",text(a,"start","?"))).append(" -> ").append(text(a,"arrival",text(a,"end","?"))); else b.append(text(a,"description","")).append("  ").append(text(a,"start","?")).append(" -> ").append(text(a,"end","?")); b.append('\n'); }
            }
        }
        if(size(s.path("nonFlyingActivities"))>0){ b.append("\nNON-FLYING ACTIVITIES\n"); for(JsonNode a:s.path("nonFlyingActivities")) b.append("  ").append(text(a,"type","ACTIVITY")).append("  ").append(text(a,"start","?")).append(" -> ").append(text(a,"end","?")).append("  ").append(text(a,"description","")).append('\n'); }
        return b.toString();
    }
    static String executionDetail(JsonNode sr){StringBuilder b=new StringBuilder(); b.append("EXECUTION DETAIL\nSchedule: ").append(text(sr,"scheduleId","UNKNOWN")).append("  Crew: ").append(text(sr,"crewId","UNKNOWN")).append("\n\n"); for(JsonNode r:sr.path("results")){ if(r.path("violation").asBoolean()||!"PASS".equalsIgnoreCase(text(r,"status",""))){ b.append(text(r,"status","STATUS")).append("  ").append(text(r,"ruleId","UNKNOWN")).append("\n  Class: ").append(text(r,"ruleClass","UNKNOWN")).append("\n  ").append(text(r,"message","")).append("\n\n"); }} return b.toString();}
}
