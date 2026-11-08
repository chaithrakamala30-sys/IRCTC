import { PassengerHeader } from "../../../components/passenger-header";
import { TrainDetailsExperience } from "../../../components/train-details-experience";

export const metadata = {
  title: "Train route · Mobile",
};

export default function MobileTrainDetailsPage() {
  return (
    <div className="page-shell">
      <PassengerHeader experience="mobile" />
      <TrainDetailsExperience device="mobile" />
      <footer className="site-footer">RailConnect is an independent passenger platform.</footer>
    </div>
  );
}
