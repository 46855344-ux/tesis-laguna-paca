#include "lagunapaca/MovingAverageFilter.h"

namespace laguna {

MovingAverageFilter::MovingAverageFilter(std::size_t windowSize)
    : window_(windowSize), next_(0), count_(0), buffer_{}, sum_(0.0) {
    if (window_ < 1) {
        window_ = 1;
    }
    if (window_ > kMaxWindow) {
        window_ = kMaxWindow;
    }
}

double MovingAverageFilter::add(double value) {
    if (count_ == window_) {
        sum_ -= buffer_[next_];  // descarta la muestra mas antigua
    } else {
        ++count_;
    }

    buffer_[next_] = value;
    sum_ += value;
    next_ = (next_ + 1) % window_;

    return average();
}

double MovingAverageFilter::average() const {
    return count_ == 0 ? 0.0 : sum_ / static_cast<double>(count_);
}

void MovingAverageFilter::reset() {
    next_ = 0;
    count_ = 0;
    sum_ = 0.0;
}

}  // namespace laguna
