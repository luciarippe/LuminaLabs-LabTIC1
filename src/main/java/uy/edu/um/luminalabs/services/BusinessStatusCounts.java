package uy.edu.um.luminalabs.services;

import uy.edu.um.luminalabs.entities.BusinessStatus;

public record BusinessStatusCounts(long pending, long approved, long denied, long suspended) {

    public long of(BusinessStatus status) {
        return switch (status) {
            case PENDING -> pending;
            case APPROVED -> approved;
            case DENIED -> denied;
            case SUSPENDED -> suspended;
        };
    }

    public long total() {
        return pending + approved + denied + suspended;
    }
}
